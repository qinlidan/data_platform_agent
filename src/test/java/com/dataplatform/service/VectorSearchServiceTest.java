package com.dataplatform.service;

import com.dataplatform.dto.SearchResultDTO;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.knowledge.dto.VectorSearchResult;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import com.dataplatform.knowledge.service.EmbeddingService;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VectorSearchService 测试类
 * Task 7 - 向量检索服务
 *
 * 测试流程：
 * 1. 查询向量化
 * 2. pgvector检索
 * 3. MySQL元数据补全
 * 4. 组装返回结果
 */
@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private KnowledgeVectorRepository vectorRepository;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @InjectMocks
    private VectorSearchService vectorSearchService;

    private float[] mockQueryEmbedding;
    private List<VectorSearchResult> mockVectorResults;
    private List<KnowledgeChunk> mockChunks;

    @BeforeEach
    void setUp() {
        // Mock查询向量
        mockQueryEmbedding = new float[1024];
        for (int i = 0; i < 1024; i++) {
            mockQueryEmbedding[i] = 0.1f;
        }

        // Mock向量检索结果
        mockVectorResults = Arrays.asList(
            createMockVectorResult(1L, "spark调优", "tunable", 0.86),
            createMockVectorResult(2L, "spark调优", "not_tunable", 0.75)
        );

        // Mock MySQL元数据
        mockChunks = Arrays.asList(
            createMockChunk(1L, "如何优化小文件", "调整写入参数...", "spark调优", "slow"),
            createMockChunk(2L, "大文件读取慢", "增加并行度...", "spark调优", "slow")
        );
    }

    @Test
    void testSearchWithoutDocTypeFilter() {
        // Given
        String query = "写文件产生大量小文件如何进行引擎调优";
        int topK = 5;

        when(embeddingService.embedQuery(query)).thenReturn(mockQueryEmbedding);
        when(vectorRepository.searchSimilar(anyString(), isNull(), eq(topK)))
            .thenReturn(mockVectorResults);
        when(chunkMapper.selectBatchIds(Arrays.asList(1L, 2L)))
            .thenReturn(mockChunks);

        // When
        List<SearchResultDTO> results = vectorSearchService.search(query, topK);

        // Then
        assertNotNull(results);
        assertEquals(2, results.size());

        // 验证第一个结果
        SearchResultDTO first = results.get(0);
        assertEquals(1L, first.getChunkId());
        assertEquals("如何优化小文件", first.getChunkText());
        assertEquals("slow", first.getChunkType());
        assertEquals("spark调优", first.getDocType());
        assertEquals(0.86f, first.getSimilarity(), 0.001);

        // 验证调用顺序和参数
        verify(embeddingService).embedQuery(query);
        verify(vectorRepository).searchSimilar(anyString(), isNull(), eq(topK));
        verify(chunkMapper).selectBatchIds(Arrays.asList(1L, 2L));
    }

    @Test
    void testSearchWithEmptyResults() {
        // Given
        String query = "不存在的查询";
        when(embeddingService.embedQuery(query)).thenReturn(mockQueryEmbedding);
        when(vectorRepository.searchSimilar(anyString(), isNull(), eq(5)))
            .thenReturn(Arrays.asList());

        // When
        List<SearchResultDTO> results = vectorSearchService.search(query, 5);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());

        // 不应该调用MySQL查询
        verify(chunkMapper, never()).selectBatchIds(anyList());
    }

    @Test
    void testSearchPreservesOrderFromPgvector() {
        // Given - pgvector返回的顺序是 chunk_id 2, 1, 3
        List<VectorSearchResult> unorderedResults = Arrays.asList(
            createMockVectorResult(2L, "spark调优", "tunable", 0.90),
            createMockVectorResult(1L, "spark调优", "tunable", 0.85),
            createMockVectorResult(3L, "spark调优", "tunable", 0.80)
        );

        List<KnowledgeChunk> chunks = Arrays.asList(
            createMockChunk(1L, "问题1", "答案1", "spark调优", "slow"),
            createMockChunk(2L, "问题2", "答案2", "spark调优", "slow"),
            createMockChunk(3L, "问题3", "答案3", "spark调优", "slow")
        );

        when(embeddingService.embedQuery(anyString())).thenReturn(mockQueryEmbedding);
        when(vectorRepository.searchSimilar(anyString(), isNull(), eq(3)))
            .thenReturn(unorderedResults);
        when(chunkMapper.selectBatchIds(Arrays.asList(2L, 1L, 3L)))
            .thenReturn(chunks);

        // When
        List<SearchResultDTO> results = vectorSearchService.search("test", 3);

        // Then - 应该保持pgvector的顺序: 2, 1, 3
        assertEquals(3, results.size());
        assertEquals(2L, results.get(0).getChunkId());
        assertEquals(1L, results.get(1).getChunkId());
        assertEquals(3L, results.get(2).getChunkId());

        // 验证分数也保持对应关系
        assertEquals(0.90, results.get(0).getSimilarity(), 0.001);
        assertEquals(0.85, results.get(1).getSimilarity(), 0.001);
        assertEquals(0.80, results.get(2).getSimilarity(), 0.001);
    }

    @Test
    void testEmbeddingConversion() {
        // Given
        String query = "测试查询";
        when(embeddingService.embedQuery(query)).thenReturn(mockQueryEmbedding);
        when(vectorRepository.searchSimilar(anyString(), isNull(), eq(5)))
            .thenReturn(Arrays.asList());

        // When
        vectorSearchService.search(query, 5);

        // Then - 验证向量被正确转换为字符串格式
        verify(vectorRepository).searchSimilar(
            argThat(embeddingStr -> {
                // 应该是 "0.1, 0.1, 0.1, ..." 格式（逗号+空格分隔）
                String[] parts = embeddingStr.split(",");
                return parts.length == 1024;
            }),
            isNull(),
            eq(5)
        );
    }

    // ==================== Helper Methods ====================

    private VectorSearchResult createMockVectorResult(Long chunkId, String docType,
                                                      String routeAction, double score) {
        return new VectorSearchResult() {
            @Override
            public Long getChunkId() {
                return chunkId;
            }

            @Override
            public String getDocType() {
                return docType;
            }

            @Override
            public String getRouteAction() {
                return routeAction;
            }

            @Override
            public Double getScore() {
                return score;
            }
        };
    }

    private KnowledgeChunk createMockChunk(Long id, String questions, String answer,
                                           String docType, String sceneType) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocType(docType);
        chunk.setDocId(docType);
        chunk.setCategory("测试分类");
        chunk.setSceneType(sceneType);
        chunk.setSceneTitle("测试场景");
        chunk.setQuestions(questions);
        chunk.setAnswer(answer);
        chunk.setSectionPath("测试分类 / 测试场景 / " + questions);
        return chunk;
    }
}
