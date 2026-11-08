package com.dataplatform.ai.tools;

import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.KnowledgeRetrieval;
import com.dataplatform.knowledge.service.KnowledgeRetrieval.Result;
import com.dataplatform.knowledge.service.RAGService;
import com.dataplatform.knowledge.service.RerankService.RerankException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * queryFaq 工具现在走知识库检索管线。
 * 工具内不再调第二次大模型：把带编号的参考资料交给外层 Agent 模型，由它组织回答并标注 [n]。
 */
@ExtendWith(MockitoExtension.class)
class FaqToolTest {

    @Mock KnowledgeRetrieval knowledgeRetrieval;

    FaqTool tool;

    @BeforeEach
    void setUp() {
        tool = new FaqTool(knowledgeRetrieval, 0.6, 0.5);
    }

    private static RerankResult rr(long id, String text) {
        return RerankResult.builder().chunkId(id).text(text).sectionPath("sec/" + id)
            .relevanceScore(0.9).vectorSimilarity(0.8).build();
    }

    private static ToolContext ctx(String conversationId) {
        return new ToolContext(Map.of("conversationId", conversationId));
    }

    @Test
    void hitReturnsPromptAndSavesCitationsUnderTheCurrentConversation() {
        List<RerankResult> refs = List.of(rr(2, "增加 executor 内存"));
        when(knowledgeRetrieval.retrieve("Spark OOM", 42L, 0.6, 0.5))
            .thenReturn(new Result(false, null, "[参考资料 1]\n增加 executor 内存\n", refs, 0.9));

        String out = tool.queryFaq("Spark OOM", null, ctx("42"));

        assertTrue(out.contains("[参考资料 1]"), out);
        assertTrue(out.contains("增加 executor 内存"), out);
        // 提示外层模型怎样用这些资料
        assertTrue(out.contains("[1]"), out);
        verify(knowledgeRetrieval).saveCitations(42L, refs);
    }

    @Test
    void hitAttachesNumberedCitationsSoTheUiCanShowWhereEachMarkerCameFrom() {
        List<RerankResult> refs = List.of(rr(2, "增加 executor 内存"), rr(1, "调大 shuffle 分区"));
        when(knowledgeRetrieval.retrieve("Spark OOM", 42L, 0.6, 0.5))
            .thenReturn(new Result(false, null, "[参考资料 1]\n增加 executor 内存\n", refs, 0.9));

        String out = tool.queryFaq("Spark OOM", null, ctx("42"));

        com.dataplatform.ai.CitationPayload.Extracted ex = com.dataplatform.ai.CitationPayload.extract(out);
        assertEquals(2, ex.citations().size());
        // 序号与提示词编号一致：重排第一名是 [1]
        assertEquals(1, ex.citations().get(0).getIndex());
        assertEquals(2L, ex.citations().get(0).getChunkId());
        assertEquals("增加 executor 内存", ex.citations().get(0).getText());
        assertEquals("sec/2", ex.citations().get(0).getSectionPath());
        assertEquals(2, ex.citations().get(1).getIndex());
        // 剥掉引用后，模型看到的文本仍是参考资料 + 使用说明
        assertTrue(ex.cleanText().contains("[参考资料 1]"), ex.cleanText());
        assertTrue(ex.cleanText().contains("[1]"), ex.cleanText());
    }

    @Test
    void refusalAttachesNoCitations() {
        when(knowledgeRetrieval.retrieve(any(), any(), anyDouble(), anyDouble()))
            .thenReturn(new Result(true, RAGService.REFUSAL_NO_INFO, null, List.of(), null));

        String out = tool.queryFaq("明天天气", null, ctx("42"));

        assertEquals(RAGService.REFUSAL_NO_INFO, out);
        assertTrue(com.dataplatform.ai.CitationPayload.extract(out).citations().isEmpty());
    }

    @Test
    void refusalReturnsRefusalMessageAndDoesNotSaveCitations() {
        when(knowledgeRetrieval.retrieve(any(), any(), anyDouble(), anyDouble()))
            .thenReturn(new Result(true, RAGService.REFUSAL_NO_INFO, null, List.of(), null));

        String out = tool.queryFaq("明天天气", null, ctx("42"));

        assertTrue(out.contains(RAGService.REFUSAL_NO_INFO), out);
        assertFalse(out.contains("[参考资料"), out);
        verify(knowledgeRetrieval, never()).saveCitations(any(), any());
    }

    @Test
    void categoryIsAppendedToTheQueryAsContext() {
        when(knowledgeRetrieval.retrieve("OOM 作业报错", 42L, 0.6, 0.5))
            .thenReturn(new Result(true, RAGService.REFUSAL_NO_INFO, null, List.of(), null));

        tool.queryFaq("OOM", "作业报错", ctx("42"));

        verify(knowledgeRetrieval).retrieve("OOM 作业报错", 42L, 0.6, 0.5);
    }

    @Test
    void blankCategoryIsIgnored() {
        when(knowledgeRetrieval.retrieve("OOM", 42L, 0.6, 0.5))
            .thenReturn(new Result(true, RAGService.REFUSAL_NO_INFO, null, List.of(), null));

        tool.queryFaq("OOM", "  ", ctx("42"));

        verify(knowledgeRetrieval).retrieve("OOM", 42L, 0.6, 0.5);
    }

    @Test
    void missingOrNonNumericConversationIdStillSearchesButSkipsConversationBinding() {
        when(knowledgeRetrieval.retrieve(eq("OOM"), isNull(), anyDouble(), anyDouble()))
            .thenReturn(new Result(false, null, "p", List.of(rr(1, "t")), 0.9));

        // 没有 ToolContext、没有 conversationId、conversationId 不是数字：都不能让工具崩溃
        assertTrue(tool.queryFaq("OOM", null, null).contains("p"));
        assertTrue(tool.queryFaq("OOM", null, new ToolContext(Map.of())).contains("p"));
        assertTrue(tool.queryFaq("OOM", null, ctx("not-a-number")).contains("p"));
        // 工具把 null 会话传下去；"会话为 null 时不保存"由 KnowledgeRetrieval.saveCitations 负责（有单独测试）
        verify(knowledgeRetrieval, times(3)).saveCitations(isNull(), any());
        verify(knowledgeRetrieval, never()).saveCitations(org.mockito.ArgumentMatchers.notNull(), any());
    }

    @Test
    void systemFailureIsNotDisguisedAsKnowledgeBaseMiss() {
        when(knowledgeRetrieval.retrieve(any(), any(), anyDouble(), anyDouble()))
            .thenThrow(new RerankException("boom"));

        // 抛出去由工具拦截器链统一处理，不能返回"没有相关信息"误导用户
        assertThrows(RerankException.class, () -> tool.queryFaq("OOM", null, ctx("42")));
    }

    @Test
    void blankKeywordReturnsGuidanceWithoutCallingRetrieval() {
        String out = tool.queryFaq("  ", null, ctx("42"));

        assertTrue(out.contains("关键词"), out);
        verify(knowledgeRetrieval, never()).retrieve(any(), any(), anyDouble(), anyDouble());
        assertEquals(out, tool.queryFaq(null, null, ctx("42")));
    }
}
