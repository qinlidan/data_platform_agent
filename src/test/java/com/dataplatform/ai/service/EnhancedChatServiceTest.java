package com.dataplatform.ai.service;

import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.KnowledgeRetrieval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EnhancedChatService 单元测试
 */
class EnhancedChatServiceTest {

    @Mock
    private KnowledgeRetrieval knowledgeRetrieval;

    @InjectMocks
    private EnhancedChatService enhancedChatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testEnhanceWithKnowledgeBase_Success() {
        // 准备测试数据
        String userQuestion = "如何创建数据源？";
        List<RerankResult> mockResults = Arrays.asList(
                RerankResult.builder()
                        .chunkId(1L)
                        .text("创建数据源需要配置连接信息...")
                        .sectionPath("数据源管理 > 创建数据源")
                        .relevanceScore(0.85)
                        .vectorSimilarity(0.78)
                        .build(),
                RerankResult.builder()
                        .chunkId(2L)
                        .text("支持的数据源类型包括 MySQL、PostgreSQL...")
                        .sectionPath("数据源管理 > 数据源类型")
                        .relevanceScore(0.75)
                        .vectorSimilarity(0.72)
                        .build()
        );

        KnowledgeRetrieval.Result mockRetrievalResult = new KnowledgeRetrieval.Result(
                false,  // refused
                null,   // refusalMessage
                "用户问题：如何创建数据源？\n\n参考资料：\n[1] 创建数据源需要配置连接信息...",  // prompt
                mockResults,  // references
                0.85    // topRelevance
        );

        when(knowledgeRetrieval.retrieve(anyString(), isNull(), anyDouble(), anyDouble()))
                .thenReturn(mockRetrievalResult);

        // 执行测试
        EnhancedChatService.ChatContext context = enhancedChatService.enhanceWithKnowledgeBase(userQuestion, 3);

        // 验证结果
        assertTrue(context.hasKnowledgeBase());
        assertFalse(context.getEnhancedSystemPrompt().isEmpty());
        assertEquals(2, context.getCitations().size());

        // 验证引用元数据
        EnhancedChatService.CitationMetadata citation1 = context.getCitations().get(0);
        assertEquals(1, citation1.getIndex());
        assertEquals("数据源管理 > 创建数据源", citation1.getTitle());
        assertEquals(0.85, citation1.getScore());

        // 验证调用
        verify(knowledgeRetrieval, times(1)).retrieve(eq(userQuestion), isNull(), eq(0.7), eq(0.5));
    }

    @Test
    void testEnhanceWithKnowledgeBase_Refused() {
        // 准备拒答场景
        String userQuestion = "无关问题？";
        KnowledgeRetrieval.Result mockRetrievalResult = new KnowledgeRetrieval.Result(
                true,    // refused
                "抱歉，知识库中没有相关信息",  // refusalMessage
                null,    // prompt
                List.of(),  // references
                0.0      // topRelevance
        );

        when(knowledgeRetrieval.retrieve(anyString(), isNull(), anyDouble(), anyDouble()))
                .thenReturn(mockRetrievalResult);

        // 执行测试
        EnhancedChatService.ChatContext context = enhancedChatService.enhanceWithKnowledgeBase(userQuestion, 3);

        // 验证结果：应该返回空上下文
        assertFalse(context.hasKnowledgeBase());
        assertTrue(context.getEnhancedSystemPrompt().isEmpty());
        assertTrue(context.getCitations().isEmpty());
    }

    @Test
    void testExtractUsedCitations() {
        // 准备测试数据
        List<EnhancedChatService.CitationMetadata> allCitations = Arrays.asList(
                createCitation(1, "标题1", "内容1"),
                createCitation(2, "标题2", "内容2"),
                createCitation(3, "标题3", "内容3")
        );

        String aiResponse = "根据 [1] 和 [3] 的说明，您可以...";

        // 执行测试
        List<EnhancedChatService.CitationMetadata> usedCitations =
                enhancedChatService.extractUsedCitations(aiResponse, allCitations);

        // 验证结果：只提取了 [1] 和 [3]
        assertEquals(2, usedCitations.size());
        assertEquals(1, usedCitations.get(0).getIndex());
        assertEquals(3, usedCitations.get(1).getIndex());
    }

    private EnhancedChatService.CitationMetadata createCitation(int index, String title, String content) {
        EnhancedChatService.CitationMetadata citation = new EnhancedChatService.CitationMetadata();
        citation.setIndex(index);
        citation.setTitle(title);
        citation.setContent(content);
        citation.setScore(0.8);
        citation.setChunkId((long) index);
        return citation;
    }
}
