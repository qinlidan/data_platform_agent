package com.dataplatform.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重排结果：保留检索阶段的 vectorSimilarity，拒答和评估都要用到。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {
    private Long chunkId;
    /** 参考资料正文（取 rerank_text，缺失时退回 text） */
    private String text;
    private String sectionPath;
    /** 重排模型给出的相关度，拒答阈值 relevanceScore < 0.5 依据 */
    private Double relevanceScore;
    /** 检索阶段的余弦相似度，原样透传 */
    private Double vectorSimilarity;
}
