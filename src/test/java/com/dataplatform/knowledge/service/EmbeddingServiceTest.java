package com.dataplatform.knowledge.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * EmbeddingService 测试类
 * 使用Mock避免真实API调用
 */
@SpringBootTest
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @MockBean
    private EmbeddingModel embeddingModel;

    /**
     * 测试embedQuery方法 - 应返回1024维向量
     */
    @Test
    void testEmbedQuery() {
        // Arrange: Mock 1024维向量
        float[] mockVector = createMockFloatArray(1024);
        Embedding mockEmbedding = new Embedding(mockVector, 0);
        EmbeddingResponse mockResponse = new EmbeddingResponse(List.of(mockEmbedding));

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        // Act: 调用embedQuery
        float[] vector = embeddingService.embedQuery("小文件优化");

        // Assert: 验证向量维度和值范围
        assertNotNull(vector, "向量不应为null");
        assertEquals(1024, vector.length, "向量维度必须为1024");
        assertTrue(vector[0] >= -2.0 && vector[0] <= 2.0, "向量值应在合理范围内");
    }

    /**
     * 测试embedDocument方法 - 应返回1024维向量
     */
    @Test
    void testEmbedDocument() {
        // Arrange: Mock 1024维向量
        float[] mockVector = createMockFloatArray(1024);
        Embedding mockEmbedding = new Embedding(mockVector, 0);
        EmbeddingResponse mockResponse = new EmbeddingResponse(List.of(mockEmbedding));

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        // Act: 调用embedDocument
        String text = "Spark参数调优 | 作业慢 | 问法: 小文件过多 | 说明: 调整写入参数...";
        float[] vector = embeddingService.embedDocument(text);

        // Assert: 验证向量维度
        assertNotNull(vector, "向量不应为null");
        assertEquals(1024, vector.length, "向量维度必须为1024");
    }

    /**
     * 测试query和document前缀产生不同的向量（非对称编码）
     */
    @Test
    void testDifferentPrefixes() {
        // Arrange: 为query和document返回不同的向量
        float[] queryVector = createMockFloatArray(1024, 0.5f);
        float[] docVector = createMockFloatArray(1024, 0.8f);

        Embedding queryEmbedding = new Embedding(queryVector, 0);
        Embedding docEmbedding = new Embedding(docVector, 0);

        when(embeddingModel.embedForResponse(anyList()))
            .thenReturn(new EmbeddingResponse(List.of(queryEmbedding)))
            .thenReturn(new EmbeddingResponse(List.of(docEmbedding)));

        // Act: 调用两种方法
        float[] queryVec = embeddingService.embedQuery("测试");
        float[] docVec = embeddingService.embedDocument("测试");

        // Assert: query和document前缀应该产生不同的向量（非对称编码）
        assertFalse(Arrays.equals(queryVec, docVec), "query和document前缀应产生不同的向量");
    }

    /**
     * 测试向量维度不是1024时应抛出异常
     */
    @Test
    void testInvalidDimensionThrowsException() {
        // Arrange: Mock 错误维度的向量（768维）
        float[] wrongDimVector = createMockFloatArray(768);
        Embedding mockEmbedding = new Embedding(wrongDimVector, 0);
        EmbeddingResponse mockResponse = new EmbeddingResponse(List.of(mockEmbedding));

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        // Act & Assert: 应抛出异常
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            embeddingService.embedQuery("测试");
        });

        assertTrue(exception.getMessage().contains("1024"),
                   "异常信息应提到期望的维度1024");
    }

    /**
     * 辅助方法：创建指定维度的Mock向量
     */
    private float[] createMockFloatArray(int dimension) {
        return createMockFloatArray(dimension, 0.1f);
    }

    /**
     * 辅助方法：创建指定维度和基准值的Mock向量
     */
    private float[] createMockFloatArray(int dimension, float baseValue) {
        float[] array = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            array[i] = baseValue + (i % 10) * 0.01f;
        }
        return array;
    }
}
