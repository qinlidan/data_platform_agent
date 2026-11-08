package com.dataplatform.ai.service;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量化服务
 * 使用 Spring AI Alibaba 的 DashScope Embedding 模型
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private final DashScopeEmbeddingModel embeddingModel;

    /**
     * 单文本向量化
     *
     * @param text 待向量化的文本
     * @return 1024 维 float 数组
     */
    public float[] embedDocument(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding 输入文本不能为空");
        }

        try {
            // 建议加上文档侧的前缀（根据你的需求，对于非对称编码模型）
            // String formattedText = "passage: " + text;
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));

            // 🔥 修复点：getOutput() 现在直接返回 float[]，直接接收即可
            float[] embedding = response.getResults().get(0).getOutput();

            log.debug("文本向量化成功: text_length={}, embedding_dim={}", text.length(), embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("文本向量化失败: text={}, error={}", text.substring(0, Math.min(text.length(), 50)), e.getMessage());
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量文本向量化
     *
     * @param texts 待向量化的文本列表
     * @return float[][] 数组，每行为一个文本的向量
     */
    public float[][] embedDocuments(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Embedding 输入文本列表不能为空");
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);

            float[][] embeddings = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                float[] embedding  = response.getResults().get(i).getOutput();
                embeddings[i] = embedding;
            }

            log.info("批量文本向量化成功: batch_size={}, embedding_dim={}", texts.size(), embeddings[0].length);
            return embeddings;

        } catch (Exception e) {
            log.error("批量文本向量化失败: batch_size={}, error={}", texts.size(), e.getMessage());
            throw new RuntimeException("批量向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询文本向量化（用于检索）
     *
     * @param query 查询文本
     * @return 1024 维 float 数组
     */
    public float[] embedQuery(String query) {
        return embedDocument(query);  // DashScope Embedding 不区分 document 和 query
    }
}
