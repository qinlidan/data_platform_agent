package com.dataplatform.knowledge.confidence;

import lombok.Builder;
import lombok.Data;

/**
 * 检索证据置信度信号
 * 包含用于计算综合置信度的多维信号
 */
@Data
@Builder
public class EvidenceConfidence {

    /**
     * 精排 Top1 的相关性分数
     */
    private Double topRelevanceScore;

    /**
     * 有效证据数量（精排返回的结果数）
     */
    private Integer validEvidenceCount;

    /**
     * Top1 和 Top2 的分差（Top2不存在时为null）
     */
    private Double scoreDelta;

    /**
     * 综合置信度分数 [0, 1]
     * 由多个信号加权计算得出
     */
    private Double overallScore;

    /**
     * 是否通过置信度闸（true=通过，false=拦截）
     */
    private Boolean passed;

    /**
     * 拒绝原因（passed=false时有值）
     */
    private String rejectionReason;
}
