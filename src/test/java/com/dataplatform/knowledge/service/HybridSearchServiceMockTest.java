package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.pgmapper.HybridSearchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HybridSearchService 单元测试（使用 Mockito）
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchServiceMockTest {

    @Mock
    private HybridSearchMapper hybridSearchMapper;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private HybridSearchService hybridSearchService;

    private void setupEmbeddingMocks() {
        // Mock 返回向量（按需调用）
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        lenient().when(embeddingService.embedQuery(anyString())).thenReturn(mockVector);
        lenient().when(embeddingService.formatVector(any(float[].class))).thenReturn("[0.1,0.2,0.3]");
    }

    @Test
    void testHybridSearch() {
        // Arrange
        setupEmbeddingMocks();

        HybridSearchResult result1 = new HybridSearchResult();
        result1.setChunkId(1L);
        result1.setText("PostgreSQL 数据库连接");
        result1.setDocType("manual");
        result1.setRouteAction("RAG");
        result1.setRrfScore(0.95);

        HybridSearchResult result2 = new HybridSearchResult();
        result2.setChunkId(2L);
        result2.setText("Redis 缓存配置");
        result2.setDocType("manual");
        result2.setRouteAction("RAG");
        result2.setRrfScore(0.85);

        when(hybridSearchMapper.hybridSearch(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(result1, result2));

        // Act
        List<HybridSearchResult> results = hybridSearchService.hybridSearch("数据库连接问题", "manual", 5);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getChunkId()).isEqualTo(1L);
        assertThat(results.get(0).getRrfScore()).isGreaterThan(results.get(1).getRrfScore());

        // 验证调用
        verify(embeddingService).embedQuery("数据库连接问题");
        verify(embeddingService).formatVector(any(float[].class));
        verify(hybridSearchMapper).hybridSearch("[0.1,0.2,0.3]", "数据库连接问题", "manual", 5);
    }

    @Test
    void testVectorSearch() {
        // Arrange
        setupEmbeddingMocks();

        HybridSearchResult result = new HybridSearchResult();
        result.setChunkId(10L);
        result.setText("向量检索结果");
        result.setDocType("manual");
        result.setScore(0.92);

        when(hybridSearchMapper.vectorSearch(anyString(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(result));

        // Act
        List<HybridSearchResult> results = hybridSearchService.vectorSearch("查询文本", "manual", 5);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo(10L);
        assertThat(results.get(0).getScore()).isEqualTo(0.92);

        // 验证调用
        verify(embeddingService).embedQuery("查询文本");
        verify(hybridSearchMapper).vectorSearch("[0.1,0.2,0.3]", "manual", 5);
    }

    @Test
    void testKeywordSearch() {
        // Arrange
        HybridSearchResult result = new HybridSearchResult();
        result.setChunkId(20L);
        result.setText("关键词检索结果");
        result.setDocType("qa");
        result.setScore(0.88);

        when(hybridSearchMapper.keywordSearch(anyString(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(result));

        // Act
        List<HybridSearchResult> results = hybridSearchService.keywordSearch("关键词", "qa", 3);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo(20L);
        assertThat(results.get(0).getScore()).isEqualTo(0.88);

        // 验证调用 - 注意 keywordSearch 不调用 embedding
        verify(embeddingService, never()).embedQuery(anyString());
        verify(hybridSearchMapper).keywordSearch("关键词", "qa", 3);
    }

    @Test
    void testDocTypeFilter() {
        // Arrange
        setupEmbeddingMocks();

        HybridSearchResult qaResult = new HybridSearchResult();
        qaResult.setChunkId(30L);
        qaResult.setText("QA 结果");
        qaResult.setDocType("qa");

        when(hybridSearchMapper.hybridSearch(anyString(), anyString(), eq("qa"), anyInt()))
                .thenReturn(Arrays.asList(qaResult));

        // Act
        List<HybridSearchResult> results = hybridSearchService.hybridSearch("问题", "qa", 5);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocType()).isEqualTo("qa");

        verify(hybridSearchMapper).hybridSearch(eq("[0.1,0.2,0.3]"), eq("问题"), eq("qa"), eq(5));
    }
}
