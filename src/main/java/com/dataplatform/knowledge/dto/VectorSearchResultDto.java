package com.dataplatform.knowledge.dto;

import java.math.BigDecimal;

/**
 * 向量检索结果 DTO
 * 用于 EntityManager 原生查询的手动结果映射
 *
 * Task 4 - Step 5 (重构为具体类以支持自定义 Repository)
 */
public class VectorSearchResultDto {

    private Long chunkId;
    private String docType;
    private String routeAction;
    private BigDecimal score;

    public VectorSearchResultDto() {
    }

    public VectorSearchResultDto(Long chunkId, String docType, String routeAction, BigDecimal score) {
        this.chunkId = chunkId;
        this.docType = docType;
        this.routeAction = routeAction;
        this.score = score;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getRouteAction() {
        return routeAction;
    }

    public void setRouteAction(String routeAction) {
        this.routeAction = routeAction;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    /**
     * 转为 Double 类型的 score（向后兼容接口定义）
     */
    public Double getScoreAsDouble() {
        return score != null ? score.doubleValue() : null;
    }
}
