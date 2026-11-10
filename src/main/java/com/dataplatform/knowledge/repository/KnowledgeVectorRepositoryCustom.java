package com.dataplatform.knowledge.repository;

import com.dataplatform.knowledge.dto.VectorSearchResult;

import java.util.List;

/**
 * 自定义 Repository 接口
 * 用于绕过 JSqlParser 限制，直接执行包含 pgvector <=> 运算符的原生 SQL
 */
public interface KnowledgeVectorRepositoryCustom {

    /**
     * 向量相似度检索（使用 pgvector <=> 运算符）
     *
     * @param embedding 查询向量字符串，格式：[0.1,0.2,...]
     * @param docType 文档类型过滤（null表示不过滤）
     * @param limit 返回top-k结果数量
     * @return 按相似度降序排列的结果列表
     */
    List<VectorSearchResult> searchSimilar(String embedding, String docType, int limit);
}
