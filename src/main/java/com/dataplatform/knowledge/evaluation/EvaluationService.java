package com.dataplatform.knowledge.evaluation;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.evaluation.EvaluationReport.Strategy;
import com.dataplatform.knowledge.service.HybridSearchService;
import com.dataplatform.knowledge.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 检索评估：对同一批标注问题比较四种检索策略的 Recall@K 与 MRR。
 *
 * 任一策略在某 case 上抛异常（含 RerankException）都只标记该 (case, 策略) 失败，
 * 不计 0 分、不退回其他策略的结果，也不中断评估。
 */
@Service
@Slf4j
public class EvaluationService {

    /** 每种策略取前 N 条 */
    static final int TOP_N = 10;
    /** Recall@K 的 K */
    static final int RECALL_K = 5;
    /** 混合+重排的粗排候选数 */
    static final int RERANK_CANDIDATES = 30;

    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final SectionResolver sectionResolver;

    public EvaluationService(HybridSearchService hybridSearchService, RerankService rerankService,
                             SectionResolver sectionResolver) {
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
        this.sectionResolver = sectionResolver;
    }

    public EvaluationReport runEvaluation(List<EvaluationCase> cases) {
        EvaluationReport report = new EvaluationReport(RECALL_K);
        if (cases == null) {
            return report;
        }
        for (EvaluationCase c : cases) {
            String bucket = c.getQueryType() == null ? "unknown" : c.getQueryType();
            String q = c.getQuery();

            evaluate(report, bucket, c, Strategy.VECTOR,
                    () -> ids(hybridSearchService.vectorSearch(q, null, TOP_N)));
            evaluate(report, bucket, c, Strategy.KEYWORD,
                    () -> ids(hybridSearchService.keywordSearch(q, null, TOP_N)));
            evaluate(report, bucket, c, Strategy.HYBRID,
                    () -> ids(hybridSearchService.hybridSearch(q, null, TOP_N)));
            evaluate(report, bucket, c, Strategy.HYBRID_RERANK, () -> {
                List<HybridSearchResult> candidates = hybridSearchService.hybridSearch(q, null, RERANK_CANDIDATES);
                List<RerankResult> reranked = rerankService.rerank(q, candidates, TOP_N);
                return rerankedIds(reranked);
            });
        }
        return report;
    }

    private void evaluate(EvaluationReport report, String bucket, EvaluationCase c,
                          Strategy strategy, Supplier<List<Long>> retrieval) {
        List<Long> retrieved;
        try {
            retrieved = retrieval.get();
        } catch (Exception e) {
            log.warn("评估失败 - strategy={}, query={}, error={}", strategy, c.getQuery(), e.toString());
            report.recordFailure(bucket, strategy, c.getQuery(), e.toString());
            return;
        }
        List<Long> truth = legacyTruth(c);
        report.recordSuccess(bucket, strategy,
                RetrievalMetrics.recallAtK(retrieved, truth, RECALL_K),
                RetrievalMetrics.mrr(retrieved, truth));
    }

    /**
     * 旧入口 runEvaluation（含 KEYWORD 策略，生产代码没有调用方，仅保留给既有测试）的标准答案。
     * 和新入口 {@link #evaluate} 用同一套章节解析，保证只有一种标注语义。
     */
    private List<Long> legacyTruth(EvaluationCase c) {
        if (!c.hasGroundTruth()) {
            return List.of();
        }
        return resolveTruth(c, hybridSearchService.listAllChunkSections());
    }

    private static List<Long> ids(List<HybridSearchResult> results) {
        List<Long> out = new ArrayList<>();
        if (results != null) {
            for (HybridSearchResult r : results) {
                out.add(r.getChunkId());
            }
        }
        return out;
    }

    private static List<Long> rerankedIds(List<RerankResult> results) {
        List<Long> out = new ArrayList<>();
        if (results != null) {
            for (RerankResult r : results) {
                out.add(r.getChunkId());
            }
        }
        return out;
    }

    // ======================= 新接口：BM25 / VECTOR / HYBRID / HYBRID_RERANK =======================

    /** 新接口的策略 key（顺序即输出顺序）。契约固定，前端依赖。旧 KEYWORD 策略不在此列。 */
    public static final List<String> STRATEGY_KEYS = List.of("BM25", "VECTOR", "HYBRID", "HYBRID_RERANK");
    /** 汇总桶 */
    public static final String ALL_BUCKET = "ALL";
    /** 每道题每个策略保留的召回条数 */
    static final int DETAIL_TOP_N = 10;

    private static final class Acc {
        int success;
        int failed;
        double sumRecall;
        double sumMrr;

        EvaluationResultDTO.StrategyMetrics toMetrics() {
            return EvaluationResultDTO.StrategyMetrics.builder()
                    .sampleCount(success)
                    .failedCount(failed)
                    .recallAtK(success == 0 ? null : sumRecall / success)
                    .mrr(success == 0 ? null : sumMrr / success)
                    .build();
        }
    }

    /**
     * 对一批用例跑四种策略并返回接口 DTO。
     * 没有 groundTruth 的用例直接跳过（计入 skippedCases，不触发任何检索，也不会调用收费的重排）。
     * 某 (用例, 策略) 抛异常只记失败，不计 0 分，不中断。
     */
    public EvaluationResultDTO evaluate(List<EvaluationCase> cases) {
        Map<String, Map<String, Acc>> buckets = new LinkedHashMap<>();
        Map<String, Acc> all = newAccMap();
        List<EvaluationResultDTO.CaseDetail> details = new ArrayList<>();
        List<EvaluationResultDTO.FailureItem> failures = new ArrayList<>();
        List<EvaluationResultDTO.UnresolvedItem> unresolved = new ArrayList<>();
        int skipped = 0;
        int refusal = 0;

        if (cases != null) {
            // 章节文字 -> chunk_id 的语料只加载一次；加载失败说明库不可用，整次评估直接失败，不能静默当成没标注
            List<com.dataplatform.knowledge.dto.ChunkCandidate> corpus = null;
            for (EvaluationCase c : cases) {
                if (c == null) {
                    skipped++;
                    continue;
                }
                if (c.isRefusalCase()) {
                    refusal++;
                    continue;
                }
                if (!c.hasGroundTruth()) {
                    skipped++;
                    continue;
                }
                if (corpus == null) {
                    corpus = hybridSearchService.listAllChunkSections();
                }
                List<Long> truth = resolveTruth(c, corpus);
                if (truth.isEmpty()) {
                    // 有标注但库里找不到对应章节：无法打分。不当 0 分（会冤枉检索），列出来让人核对标注文字
                    log.warn("评估跳过 - 标注的章节在库里解析不到任何 chunk, query={}, expect={}",
                            c.getQuery(), c.expectSections());
                    unresolved.add(EvaluationResultDTO.UnresolvedItem.builder()
                            .query(c.getQuery()).expectSections(c.expectSections()).build());
                    continue;
                }
                String category = c.resolvedCategory();
                Map<String, Acc> bucket = buckets.computeIfAbsent(category, k -> newAccMap());
                String q = c.getQuery();

                Map<String, Supplier<List<Long>>> retrievals = new LinkedHashMap<>();
                retrievals.put("BM25", () -> ids(hybridSearchService.bm25Search(q, null, TOP_N)));
                retrievals.put("VECTOR", () -> ids(hybridSearchService.vectorSearch(q, null, TOP_N)));
                retrievals.put("HYBRID", () -> ids(hybridSearchService.hybridSearch(q, null, TOP_N)));
                retrievals.put("HYBRID_RERANK", () -> {
                    List<HybridSearchResult> candidates = hybridSearchService.hybridSearch(q, null, RERANK_CANDIDATES);
                    return rerankedIds(rerankService.rerank(q, candidates, TOP_N));
                });

                Map<String, EvaluationResultDTO.CaseStrategyResult> results = new LinkedHashMap<>();
                for (Map.Entry<String, Supplier<List<Long>>> e : retrievals.entrySet()) {
                    String key = e.getKey();
                    try {
                        List<Long> retrieved = e.getValue().get();
                        double recall = RetrievalMetrics.recallAtK(retrieved, truth, RECALL_K);
                        double mrr = RetrievalMetrics.mrr(retrieved, truth);
                        for (Acc a : List.of(bucket.get(key), all.get(key))) {
                            a.success++;
                            a.sumRecall += recall;
                            a.sumMrr += mrr;
                        }
                        results.put(key, EvaluationResultDTO.CaseStrategyResult.builder()
                                .success(true)
                                .recallAtK(recall)
                                .mrr(mrr)
                                .retrievedChunkIds(new ArrayList<>(
                                        retrieved.subList(0, Math.min(DETAIL_TOP_N, retrieved.size()))))
                                .firstHitRank(firstHitRank(retrieved, truth))
                                .build());
                    } catch (Exception ex) {
                        log.warn("评估失败 - strategy={}, query={}, error={}", key, q, ex.toString());
                        bucket.get(key).failed++;
                        all.get(key).failed++;
                        String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        failures.add(EvaluationResultDTO.FailureItem.builder()
                                .query(q).strategy(key).message(msg).build());
                        results.put(key, EvaluationResultDTO.CaseStrategyResult.builder()
                                .success(false).error(msg).build());
                    }
                }
                details.add(EvaluationResultDTO.CaseDetail.builder()
                        .query(q)
                        .category(category)
                        .docType(c.getDocType())
                        .expectSections(c.expectSections())
                        .groundTruth(truth)
                        .results(results)
                        .build());
            }
        }

        Map<String, Map<String, EvaluationResultDTO.StrategyMetrics>> summary = new LinkedHashMap<>();
        buckets.forEach((name, accs) -> summary.put(name, toMetrics(accs)));
        summary.put(ALL_BUCKET, toMetrics(all));

        return EvaluationResultDTO.builder()
                .recallK(RECALL_K)
                .totalCases(details.size())
                .skippedCases(skipped)
                .refusalCases(refusal)
                .unresolvedCases(unresolved)
                .summary(summary)
                .cases(details)
                .failures(failures)
                .build();
    }

    /** 把一道题的全部章节标注解析成 chunk_id 并集（保持顺序、去重）。 */
    private List<Long> resolveTruth(EvaluationCase c, List<com.dataplatform.knowledge.dto.ChunkCandidate> corpus) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (String section : c.expectSections()) {
            ids.addAll(sectionResolver.resolve(section, corpus));
        }
        return new ArrayList<>(ids);
    }

    private static Map<String, Acc> newAccMap() {
        Map<String, Acc> m = new LinkedHashMap<>();
        for (String k : STRATEGY_KEYS) {
            m.put(k, new Acc());
        }
        return m;
    }

    private static Map<String, EvaluationResultDTO.StrategyMetrics> toMetrics(Map<String, Acc> accs) {
        Map<String, EvaluationResultDTO.StrategyMetrics> m = new LinkedHashMap<>();
        accs.forEach((k, a) -> m.put(k, a.toMetrics()));
        return m;
    }

    private static Integer firstHitRank(List<Long> retrieved, List<Long> truth) {
        Set<Long> t = new HashSet<>(truth);
        for (int i = 0; i < retrieved.size(); i++) {
            if (retrieved.get(i) != null && t.contains(retrieved.get(i))) {
                return i + 1;
            }
        }
        return null;
    }
}
