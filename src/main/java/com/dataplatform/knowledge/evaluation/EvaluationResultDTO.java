package com.dataplatform.knowledge.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 四策略对比评估的接口响应。后端接口与前端页面共用这一份契约。
 *
 * 结构：category（问题类别）× strategy（检索策略）× 指标。
 * 策略 key 固定为：BM25 | VECTOR | HYBRID | HYBRID_RERANK。
 * 类别 key 即用例里的 category（colloquial 口语化 / platform 平台问题 / error_keyword 专业报错关键字），
 * 另有一个汇总类别 "ALL"。
 *
 * 失败的 (用例, 策略) 不计 0 分，只计入 failedCount；平均值只统计成功的样本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResultDTO {

    /** Recall@K 的 K */
    private int recallK;
    /** 参与评估的用例总数（含无标注被跳过的以外的全部） */
    private int totalCases;
    /** 因完全没有标注被跳过的用例数（这类用例无法打分，不计入任何平均值） */
    private int skippedCases;
    /** 标注为"应拒答"的用例数：不是召回评估对象，留给拒答阈值校准，不算漏标注 */
    private int refusalCases;
    /** 有章节标注但在库里解析不到任何 chunk 的用例：不打分、不当 0 分，单独列出让人核对标注文字 */
    private List<UnresolvedItem> unresolvedCases;

    /** category -> strategy -> 汇总指标 */
    private Map<String, Map<String, StrategyMetrics>> summary;

    /** 逐题明细，便于点开某题看四种策略各自召回了什么 */
    private List<CaseDetail> cases;

    /** 失败记录 */
    private List<FailureItem> failures;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyMetrics {
        /** 成功样本数 */
        private int sampleCount;
        /** 失败样本数（已从平均值中排除） */
        private int failedCount;
        /** 成功样本的 Recall@K 平均；无成功样本时为 null */
        private Double recallAtK;
        /** 成功样本的 MRR 平均；无成功样本时为 null */
        private Double mrr;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseDetail {
        private String query;
        /** colloquial | platform | error_keyword */
        private String category;
        /** 用例所属文档类型标签，仅用于页面分组展示，不参与检索过滤 */
        private String docType;
        /** 人工标注的章节文字（原文，供页面展示） */
        private List<String> expectSections;
        /** 章节解析出的标准答案 chunk_id（用于打分和高亮命中） */
        private List<Long> groundTruth;
        /** strategy -> 该策略在这道题上的结果 */
        private Map<String, CaseStrategyResult> results;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseStrategyResult {
        /** 成功为 true；失败时 error 有值，其余指标为 null */
        private boolean success;
        private String error;
        private Double recallAtK;
        private Double mrr;
        /** 召回的 chunkId，按名次排序 */
        private List<Long> retrievedChunkIds;
        /** 第一个命中标准答案的名次（从 1 开始），未命中为 null */
        private Integer firstHitRank;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnresolvedItem {
        private String query;
        /** 解析不到 chunk 的标注文字，原样返回，便于对照文档修正 */
        private List<String> expectSections;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureItem {
        private String query;
        private String strategy;
        private String message;
    }
}
