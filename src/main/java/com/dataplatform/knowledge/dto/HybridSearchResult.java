package com.dataplatform.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 混合检索结果 DTO
 *
 * 包含向量检索、关键词检索和 RRF 融合的完整信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchResult {
    private Long chunkId;
    private String docType;
    private String routeAction;
    private String text;         // 全文检索字段
    private String rerankText;   // 精排字段
    private String sectionPath;  // 章节路径（用于引用溯源）
    private Double score;        // 单路检索时为向量相似度/关键词评分；混合检索时等于 rrfScore
    private Integer vectorRank;  // 向量检索排名（仅关键词命中时为 null）
    private Integer keywordRank; // 关键词检索排名（仅向量命中时为 null）
    private Double rrfScore;     // RRF 融合得分（只编码名次，不能用于拒答阈值）
    private Double vectorSimilarity; // 余弦相似度 1-(embedding<=>query)，拒答阈值（<0.6）依据；仅关键词命中的行也会计算
}
