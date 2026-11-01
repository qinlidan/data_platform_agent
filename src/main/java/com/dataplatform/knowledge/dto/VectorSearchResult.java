package com.dataplatform.knowledge.dto;

/**
 * 向量检索结果投影接口
 * 用于 Spring Data JPA 原生查询的结果映射
 *
 * Task 4 - Step 5
 */
public interface VectorSearchResult {

    /**
     * chunk ID
     */
    Long getChunkId();

    /**
     * 文档类型（spark调优 / 数据研发平台用户手册）
     */
    String getDocType();

    /**
     * 路由动作（tunable / not_tunable / escalate / guide）
     */
    String getRouteAction();

    /**
     * 相似度得分（0-1之间，1表示完全相同）
     * 计算方式：1 - (embedding <=> query_vector)
     */
    Double getScore();
}
