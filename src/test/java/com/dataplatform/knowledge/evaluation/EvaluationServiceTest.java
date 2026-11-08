package com.dataplatform.knowledge.evaluation;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.HybridSearchService;
import com.dataplatform.knowledge.service.RerankService;
import com.dataplatform.knowledge.service.RerankService.RerankException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 同一个 mock 方法会以多组参数被调用（不同 query），严格模式会报 PotentialStubbingProblem，故用宽松模式
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationServiceTest {

    private static final double EPS = 1e-9;

    @Mock
    private HybridSearchService hybridSearchService;
    @Mock
    private RerankService rerankService;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(hybridSearchService, rerankService, new SectionResolver());
        // 固定语料：chunk i 的章节是「手册 / 模块i / 小节i」，用例的 Long 标注被翻译成对应章节
        when(hybridSearchService.listAllChunkSections()).thenReturn(corpus());
    }

    // ---------- helpers ----------

    private static List<HybridSearchResult> hits(Long... ids) {
        List<HybridSearchResult> list = new ArrayList<>();
        for (Long id : ids) {
            list.add(HybridSearchResult.builder().chunkId(id).text("t" + id).build());
        }
        return list;
    }

    private static List<RerankResult> reranked(Long... ids) {
        List<RerankResult> list = new ArrayList<>();
        double score = 0.99;
        for (Long id : ids) {
            list.add(RerankResult.builder().chunkId(id).text("t" + id).relevanceScore(score).build());
            score -= 0.01;
        }
        return list;
    }

    private static List<com.dataplatform.knowledge.dto.ChunkCandidate> corpus() {
        List<com.dataplatform.knowledge.dto.ChunkCandidate> l = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            com.dataplatform.knowledge.dto.ChunkCandidate cc = new com.dataplatform.knowledge.dto.ChunkCandidate();
            cc.setChunkId(i);
            cc.setSectionPath("手册 / 模块" + i + " / 小节" + i);
            l.add(cc);
        }
        return l;
    }

    private static EvaluationCase caseOf(String query, String type, Long... gt) {
        List<GroundTruthItem> g = new ArrayList<>();
        for (Long id : gt) g.add(GroundTruthItem.section("模块" + id + ",小节" + id));
        return EvaluationCase.builder()
                .query(query)
                .difficulty("easy")
                .queryType(type)
                .groundTruth(g)
                .build();
    }

    // ---------- 1. 四种策略各自调用对应方法 ----------

    @Test
    void eachStrategyCallsItsOwnSearchMethod() {
        List<HybridSearchResult> candidates = hits(1L, 2L, 3L);
        when(hybridSearchService.vectorSearch("q", null, 10)).thenReturn(hits(1L));
        when(hybridSearchService.keywordSearch("q", null, 10)).thenReturn(hits(1L));
        when(hybridSearchService.hybridSearch("q", null, 10)).thenReturn(hits(1L));
        when(hybridSearchService.hybridSearch("q", null, 30)).thenReturn(candidates);
        when(rerankService.rerank("q", candidates, 10)).thenReturn(reranked(1L));

        EvaluationReport report = service.runEvaluation(List.of(caseOf("q", "keyword", 1L)));

        // 纯向量、纯关键词必须直接调用对应方法，而不是用 hybridSearch 模拟
        verify(hybridSearchService, times(1)).vectorSearch("q", null, 10);
        verify(hybridSearchService, times(1)).keywordSearch("q", null, 10);
        // 混合：top10；混合+重排：候选 30，再 rerank 取 10
        verify(hybridSearchService, times(1)).hybridSearch("q", null, 10);
        verify(hybridSearchService, times(1)).hybridSearch("q", null, 30);
        verify(hybridSearchService, times(2)).hybridSearch(anyString(), isNull(), anyInt());
        verify(rerankService, times(1)).rerank("q", candidates, 10);

        for (EvaluationReport.Strategy s : EvaluationReport.Strategy.values()) {
            EvaluationReport.StrategyStats st = report.getOverall(s);
            assertThat(st.getSuccessCount()).as(s.name()).isEqualTo(1);
            assertThat(st.getFailedCount()).as(s.name()).isZero();
            assertThat(st.getAvgRecallAtK()).as(s.name()).isCloseTo(1.0, within(EPS));
            assertThat(st.getAvgMrr()).as(s.name()).isCloseTo(1.0, within(EPS));
        }
    }

    @Test
    void rerankStrategyUsesRerankOrderNotHybridOrder() {
        List<HybridSearchResult> candidates = hits(9L, 8L, 1L);
        when(hybridSearchService.hybridSearch("q", null, 10)).thenReturn(hits(9L, 8L, 1L)); // 混合：第 3 位命中
        when(hybridSearchService.hybridSearch("q", null, 30)).thenReturn(candidates);
        when(rerankService.rerank("q", candidates, 10)).thenReturn(reranked(1L, 9L, 8L)); // 重排：第 1 位命中

        EvaluationReport report = service.runEvaluation(List.of(caseOf("q", "semantic", 1L)));

        assertThat(report.getOverall(EvaluationReport.Strategy.HYBRID).getAvgMrr()).isCloseTo(1.0 / 3, within(EPS));
        assertThat(report.getOverall(EvaluationReport.Strategy.HYBRID_RERANK).getAvgMrr()).isCloseTo(1.0, within(EPS));
    }

    // ---------- 2. 按 queryType 分桶平均 ----------

    @Test
    void averagesAreBucketedByQueryType() {
        // keyword 桶: A(gt=1) 向量 [1] -> R=1, MRR=1；B(gt=2) 向量 [9,8,2] -> R=1, MRR=1/3
        // semantic 桶: C(gt=3) 向量 [9] -> R=0, MRR=0
        when(hybridSearchService.vectorSearch("A", null, 10)).thenReturn(hits(1L));
        when(hybridSearchService.vectorSearch("B", null, 10)).thenReturn(hits(9L, 8L, 2L));
        when(hybridSearchService.vectorSearch("C", null, 10)).thenReturn(hits(9L));
        // 关键词策略: A 第 6 位命中 -> R@5=0, MRR=1/6
        when(hybridSearchService.keywordSearch("A", null, 10)).thenReturn(hits(9L, 8L, 7L, 6L, 5L, 1L));

        EvaluationReport report = service.runEvaluation(List.of(
                caseOf("A", "keyword", 1L),
                caseOf("B", "keyword", 2L),
                caseOf("C", "semantic", 3L)));

        EvaluationReport.Strategy vec = EvaluationReport.Strategy.VECTOR;
        assertThat(report.getStats("keyword", vec).getAvgRecallAtK()).isCloseTo(1.0, within(EPS));
        assertThat(report.getStats("keyword", vec).getAvgMrr()).isCloseTo((1.0 + 1.0 / 3) / 2, within(EPS));
        assertThat(report.getStats("keyword", vec).getSuccessCount()).isEqualTo(2);
        assertThat(report.getStats("semantic", vec).getAvgRecallAtK()).isCloseTo(0.0, within(EPS));
        assertThat(report.getStats("semantic", vec).getAvgMrr()).isCloseTo(0.0, within(EPS));
        assertThat(report.getStats("semantic", vec).getSuccessCount()).isEqualTo(1);
        // 总体: R = (1+1+0)/3, MRR = (1 + 1/3 + 0)/3
        assertThat(report.getOverall(vec).getAvgRecallAtK()).isCloseTo(2.0 / 3, within(EPS));
        assertThat(report.getOverall(vec).getAvgMrr()).isCloseTo((4.0 / 3) / 3, within(EPS));
        assertThat(report.getOverall(vec).getSuccessCount()).isEqualTo(3);

        // 关键词策略: keyword 桶 A 第 6 位 (R@5=0, MRR=1/6)，B 未命中(0,0)
        EvaluationReport.Strategy kw = EvaluationReport.Strategy.KEYWORD;
        assertThat(report.getStats("keyword", kw).getAvgRecallAtK()).isCloseTo(0.0, within(EPS));
        assertThat(report.getStats("keyword", kw).getAvgMrr()).isCloseTo((1.0 / 6) / 2, within(EPS));

        assertThat(report.getBucketNames()).containsExactlyInAnyOrder("keyword", "semantic");
    }

    // ---------- 3. 重排失败：标记失败，排除出平均，其他策略不受影响 ----------

    @Test
    void rerankFailureIsExcludedFromAverageAndOthersUnaffected() {
        // 三个同桶 case，gt 分别为 1/2/3；混合都完美命中第 1 位
        for (String q : List.of("A", "B", "C")) {
            long id = q.charAt(0) - 'A' + 1;
            when(hybridSearchService.vectorSearch(q, null, 10)).thenReturn(hits(id));
            when(hybridSearchService.keywordSearch(q, null, 10)).thenReturn(hits(id));
            when(hybridSearchService.hybridSearch(q, null, 10)).thenReturn(hits(id));
            when(hybridSearchService.hybridSearch(q, null, 30)).thenReturn(hits(id));
        }
        // A 重排成功且命中第 1 位；B 重排抛异常；C 重排成功但未命中（返回 99）
        when(rerankService.rerank(eq("A"), anyList(), eq(10))).thenReturn(reranked(1L));
        when(rerankService.rerank(eq("B"), anyList(), eq(10))).thenThrow(new RerankException("boom"));
        when(rerankService.rerank(eq("C"), anyList(), eq(10))).thenReturn(reranked(99L));

        EvaluationReport report = service.runEvaluation(List.of(
                caseOf("A", "hybrid", 1L),
                caseOf("B", "hybrid", 2L),
                caseOf("C", "hybrid", 3L)));

        EvaluationReport.StrategyStats rr = report.getStats("hybrid", EvaluationReport.Strategy.HYBRID_RERANK);
        assertThat(rr.getFailedCount()).isEqualTo(1);
        assertThat(rr.getSuccessCount()).isEqualTo(2);
        // 平均只对 A、C: R=(1+0)/2。若 B 被当 0 分计入会得到 1/3，若退回混合结果会得到 2/3
        assertThat(rr.getAvgRecallAtK()).isCloseTo(0.5, within(EPS));
        assertThat(rr.getAvgMrr()).isCloseTo(0.5, within(EPS));
        assertThat(report.getOverall(EvaluationReport.Strategy.HYBRID_RERANK).getFailedCount()).isEqualTo(1);

        // 其他三个策略 3 个 case 全部成功，且 B 的分数照常计入
        for (EvaluationReport.Strategy s : List.of(
                EvaluationReport.Strategy.VECTOR,
                EvaluationReport.Strategy.KEYWORD,
                EvaluationReport.Strategy.HYBRID)) {
            EvaluationReport.StrategyStats st = report.getStats("hybrid", s);
            assertThat(st.getFailedCount()).as(s.name()).isZero();
            assertThat(st.getSuccessCount()).as(s.name()).isEqualTo(3);
            assertThat(st.getAvgRecallAtK()).as(s.name()).isCloseTo(1.0, within(EPS));
        }

        String text = report.toText();
        assertThat(text).contains("(1 个失败已排除)");
        // 只有重排策略有失败，提示只应出现一次
        // 出现两次：该分桶一次 + 总体一次
        assertThat(text.split("个失败已排除", -1)).hasSize(3);
    }

    @Test
    void allRerankFailedYieldsNoAverage() {
        when(hybridSearchService.hybridSearch("A", null, 30)).thenReturn(hits(1L));
        when(rerankService.rerank(eq("A"), anyList(), eq(10))).thenThrow(new RerankException("boom"));

        EvaluationReport report = service.runEvaluation(List.of(caseOf("A", "keyword", 1L)));

        EvaluationReport.StrategyStats rr = report.getOverall(EvaluationReport.Strategy.HYBRID_RERANK);
        assertThat(rr.getSuccessCount()).isZero();
        assertThat(rr.getFailedCount()).isEqualTo(1);
        assertThat(rr.hasData()).isFalse();
        assertThat(report.toText()).contains("N/A");
    }

    // ---------- 4. 单个 case 检索异常不中断评估 ----------

    @Test
    void searchExceptionOnOneCaseDoesNotAbortEvaluation() {
        when(hybridSearchService.keywordSearch("A", null, 10)).thenThrow(new IllegalStateException("db down"));
        when(hybridSearchService.keywordSearch("B", null, 10)).thenReturn(hits(2L));
        when(hybridSearchService.vectorSearch("A", null, 10)).thenReturn(hits(1L));
        when(hybridSearchService.vectorSearch("B", null, 10)).thenReturn(hits(2L));

        EvaluationReport report = service.runEvaluation(List.of(
                caseOf("A", "keyword", 1L),
                caseOf("B", "keyword", 2L)));

        EvaluationReport.StrategyStats kw = report.getStats("keyword", EvaluationReport.Strategy.KEYWORD);
        assertThat(kw.getFailedCount()).isEqualTo(1);
        assertThat(kw.getSuccessCount()).isEqualTo(1);
        assertThat(kw.getAvgRecallAtK()).isCloseTo(1.0, within(EPS)); // 只有 B

        // A 在向量策略上仍然成功，B 也被评估到了
        EvaluationReport.StrategyStats vec = report.getStats("keyword", EvaluationReport.Strategy.VECTOR);
        assertThat(vec.getSuccessCount()).isEqualTo(2);
        assertThat(vec.getFailedCount()).isZero();
        verify(hybridSearchService, times(1)).vectorSearch("B", null, 10);
    }

    @Test
    void emptyCaseListProducesEmptyReport() {
        EvaluationReport report = service.runEvaluation(List.of());
        assertThat(report.getBucketNames()).isEmpty();
        assertThat(report.getOverall(EvaluationReport.Strategy.VECTOR).getSuccessCount()).isZero();
    }

    @Test
    void toTextShowsAllStrategiesPerBucketAndOverall() {
        when(hybridSearchService.vectorSearch("A", null, 10)).thenReturn(hits(1L));

        String text = service.runEvaluation(List.of(caseOf("A", "keyword", 1L))).toText();

        assertThat(text).contains("Query Type: keyword");
        assertThat(text).contains("Vector:").contains("Keyword:").contains("Hybrid:").contains("Hybrid+Rerank:");
        assertThat(text).contains("R@5=1.00");
        assertThat(text).contains("MRR=1.00");
        assertThat(text).contains("Overall");
    }
}
