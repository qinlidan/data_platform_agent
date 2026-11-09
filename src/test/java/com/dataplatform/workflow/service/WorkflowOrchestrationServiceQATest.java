package com.dataplatform.workflow.service;

import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.KnowledgeRetrieval;
import com.dataplatform.workflow.config.WorkflowProperties;
import com.dataplatform.workflow.dto.Domain;
import com.dataplatform.workflow.dto.IntentClassificationResult;
import com.dataplatform.workflow.dto.IntentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class WorkflowOrchestrationServiceQATest {

    @Autowired
    private WorkflowOrchestrationService service;

    @Autowired
    private WorkflowProperties workflowProperties;

    @MockBean
    private KnowledgeRetrieval knowledgeRetrieval;

    @MockBean
    private IntentClassificationService intentClassificationService;

    @Test
    void testQAWithHighConfidence_PassesGate() {
        // Mock intent classification to return QA domain
        when(intentClassificationService.classify(anyString()))
            .thenReturn(new IntentClassificationResult(Domain.QA, IntentType.NONE));

        // Mock KnowledgeRetrieval.retrieve() returning high confidence result
        List<RerankResult> mockReferences = List.of(
            RerankResult.builder()
                .chunkId(1L)
                .text("Spark executor memory can be set with spark.executor.memory")
                .relevanceScore(0.85)
                .build()
        );

        when(knowledgeRetrieval.retrieve(anyString(), anyLong(), eq(0.5), eq(0.6)))
            .thenReturn(new KnowledgeRetrieval.Result(
                false,
                null,
                "[参考资料 1]\nSpark executor memory...\n\n用户问题: Spark内存怎么设置",
                mockReferences,
                0.85
            ));

        Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
            "Spark内存怎么设置", "Spark内存怎么设置", 1L, "user1", null
        );

        // Verify returned stream (with placeholder Agent response)
        List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
        assertNotNull(events);
        assertTrue(events.size() >= 2, "Should have at least text + done events");

        // Verify text event contains response
        assertEquals("text", events.get(0).event());
        assertNotNull(events.get(0).data().get("content"));

        // Verify done event
        assertEquals("done", events.get(events.size() - 1).event());

        // Verify saveCitations was called
        verify(knowledgeRetrieval, times(1)).saveCitations(eq(1L), eq(mockReferences));
    }

    @Test
    void testQAWithLowConfidence_ReturnsRefusal() {
        // Mock intent classification to return QA domain
        when(intentClassificationService.classify(anyString()))
            .thenReturn(new IntentClassificationResult(Domain.QA, IntentType.NONE));

        // Mock KnowledgeRetrieval.retrieve() returning refused result
        when(knowledgeRetrieval.retrieve(anyString(), anyLong(), eq(0.5), eq(0.6)))
            .thenReturn(new KnowledgeRetrieval.Result(
                true,
                "抱歉，知识库中没有相关信息",
                null,
                List.of(),
                null
            ));

        Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
            "未知问题", "未知问题", 1L, "user1", null
        );

        List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
        assertNotNull(events);
        assertEquals(2, events.size(), "Should have text + done events");

        // Check refusal message
        assertEquals("text", events.get(0).event());
        assertTrue(events.get(0).data().get("content").toString().contains("抱歉"),
            "Should contain refusal message");

        assertEquals("done", events.get(1).event());

        // Verify saveCitations was NOT called
        verify(knowledgeRetrieval, never()).saveCitations(anyLong(), anyList());
    }

    @Test
    void testQAUsesConfiguredThresholds() {
        // Mock intent classification to return QA domain
        when(intentClassificationService.classify(anyString()))
            .thenReturn(new IntentClassificationResult(Domain.QA, IntentType.NONE));

        when(knowledgeRetrieval.retrieve(anyString(), anyLong(), anyDouble(), anyDouble()))
            .thenReturn(new KnowledgeRetrieval.Result(
                true,
                "test",
                null,
                List.of(),
                null
            ));

        service.orchestrate("test", "test", 1L, "user1", null).collectList().block();

        // Verify thresholds from config were used
        verify(knowledgeRetrieval, times(1)).retrieve(
            eq("test"),
            eq(1L),
            eq(workflowProperties.getRag().getVectorSimThreshold()),
            eq(workflowProperties.getRag().getRerankThreshold())
        );
    }
}
