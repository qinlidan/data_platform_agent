package com.dataplatform.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding 服务 - 集成 Spring AI DashScope
 * 使用 qwen3.7-text-embedding-flash 模型生成 1024 维向量
 *
 * @author Task 3
 * @date 2026-11-01
 */
@Service("knowledgeEmbeddingService")
@Slf4j
public class EmbeddingService {

    /**
     * 期望的向量维度
     */
    private static final int EXPECTED_DIMENSION = 1024;

    /**
     * Spring AI 注入的 EmbeddingModel
     */
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(@Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 为文档生成向量（添加 passage: 前缀）
     *
     * @param text 文档文本
     * @return 1024维向量
     * @throws IllegalStateException 如果返回的向量维度不是1024
     */
    public float[] embedDocument(String text) {
        String docText = "passage: " + text;
        log.debug("Embedding document text (length: {})", text.length());

        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(docText));
        // Spring AI Alibaba 的 getOutput() 直接返回 float[]
        float[] vector = response.getResults().get(0).getOutput();

        // 验证维度
        if (vector.length != EXPECTED_DIMENSION) {
            throw new IllegalStateException(
                String.format("Embedding dimension mismatch: expected %d but got %d",
                             EXPECTED_DIMENSION, vector.length)
            );
        }

        return vector;
    }

    /**
     * 为查询生成向量（添加 query: 前缀）
     *
     * @param query 查询文本
     * @return 1024维向量
     * @throws IllegalStateException 如果返回的向量维度不是1024
     */
    public float[] embedQuery(String query) {
        String queryText = "query: " + query;
        log.debug("Embedding query text (length: {})", query.length());

        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(queryText));
        // Spring AI Alibaba 的 getOutput() 直接返回 float[]
        float[] vector = response.getResults().get(0).getOutput();

        // 验证维度
        if (vector.length != EXPECTED_DIMENSION) {
            throw new IllegalStateException(
                String.format("Embedding dimension mismatch: expected %d but got %d",
                             EXPECTED_DIMENSION, vector.length)
            );
        }

        return vector;
    }
}
