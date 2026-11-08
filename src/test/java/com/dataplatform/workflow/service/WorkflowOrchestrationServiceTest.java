package com.dataplatform.workflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkflowOrchestrationServiceTest {

    @Autowired
    private WorkflowOrchestrationService service;

    @Test
    void testChitchatReturnsFixedResponse() {
        Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
            "你好", "你好", 1L, "user1", null
        );

        List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
        assertNotNull(events);
        assertEquals(2, events.size(), "Should have text + done events");

        // Check text event
        assertEquals("text", events.get(0).event());
        assertTrue(events.get(0).data().get("content").toString().contains("聊天"),
            "Response should contain '聊天'");

        // Check done event
        assertEquals("done", events.get(1).event());
    }

    @Test
    void testOtherDomainReturnsFixedResponse() {
        Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
            "今天天气怎么样", "今天天气怎么样", 1L, "user1", null
        );

        List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
        assertNotNull(events);
        assertTrue(events.size() >= 2, "Should have at least text + done events");

        // Check text event
        assertEquals("text", events.get(0).event());
        assertTrue(events.get(0).data().get("content").toString().contains("抱歉"),
            "Response should contain '抱歉'");
    }
}
