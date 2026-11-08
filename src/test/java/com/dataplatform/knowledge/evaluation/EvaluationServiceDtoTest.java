package com.dataplatform.knowledge.evaluation;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.evaluation.EvaluationResultDTO.CaseDetail;
import com.dataplatform.knowledge.evaluation.EvaluationResultDTO.CaseStrategyResult;
import com.dataplatform.knowledge.evaluation.EvaluationResultDTO.StrategyMetrics;
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
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 新接口 evaluate(...) -> EvaluationResultDTO 的行为。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationServiceDtoTest {

    private static final double EPS = 1e-9;

    @Mock private HybridSearchService search;
    @Mock private RerankService rerank;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(search, rerank, new SectionResolver());
        // 默认让 search.listAllChunkSections() 返回一个固定语料：章节 -> chunk_id 见 corpus()
        when(search.listAllChunkSections()).thenReturn(corpus());
    }

    private static List<HybridSearchResult> hits(Long... ids) {
        List<HybridSearchResult> l = new ArrayList<>();
        for (Long id : ids) {
            l.add(HybridSearchResult.builder().chunkId(id).build());
        }
        return l;
    }

    private static List<RerankResult> reranked(Long... ids) {
        List<RerankResult> l = new ArrayList<>();
        for (Long id : ids) {
            l.add(RerankResult.builder().chunkId(id).relevanceScore(0.9).build());
        }
        return l;
    }

    private static EvaluationCase c(String q, String category, Long... gt) {
        List<GroundTruthItem> g = new ArrayList<>();
        for (Long id : gt) g.add(GroundTruthItem.section("模块" + id + ",小节" + id));
        return EvaluationCase.builder().query(q).category(category).docType("manual").groundTruth(g).build();
    }

    /** 固定语料：chunk 1..9，每个 chunk 一个独立章节，路径形如 \"手册 / 模块N / 小节N\" */
    private static List<com.dataplatform.knowledge.dto.ChunkCandidate> corpus() {
        List<com.dataplatform.knowledge.dto.ChunkCandidate> l = new ArrayList<>();
        for (long i = 1; i <= 9; i++) {
            com.dataplatform.knowledge.dto.ChunkCandidate cc = new com.dataplatform.knowledge.dto.ChunkCandidate();
            cc.setChunkId(i);
            cc.setSectionPath("手册 / 模块" + i + " / 小节" + i);
            l.add(cc);
        }
        return l;
    }

    private static EvaluationCase sec(String q, String category, String... sections) {
        List<GroundTruthItem> g = new ArrayList<>();
        for (String s : sections) g.add(GroundTruthItem.section(s));
        return EvaluationCase.builder().query(q).category(category).docType("manual").groundTruth(g).build();
    }

    private static CaseDetail detail(EvaluationResultDTO r, String q) {
        return r.getCases().stream().filter(d -> d.getQuery().equals(q)).findFirst().orElseThrow();
    }

    /** 全部策略对 q 都返回 [ids]，重排返回 rerankedIds */
    private void stubAll(String q, List<HybridSearchResult> r, List<RerankResult> rr) {
        when(search.bm25Search(q, null, 10)).thenReturn(r);
        when(search.vectorSearch(q, null, 10)).thenReturn(r);
        when(search.hybridSearch(q, null, 10)).thenReturn(r);
        when(search.hybridSearch(q, null, 30)).thenReturn(r);
        when(rerank.rerank(eq(q), anyList(), eq(10))).thenReturn(rr);
    }

    @Test
    void usesFourStrategiesWithFixedKeysAndNeverCallsLegacyKeyword() {
        stubAll("q", hits(1L), reranked(1L));

        EvaluationResultDTO r = service.evaluate(List.of(c("q", "platform", 1L)));

        assertThat(r.getRecallK()).isEqualTo(5);
        assertThat(detail(r, "q").getResults().keySet())
                .containsExactly("BM25", "VECTOR", "HYBRID", "HYBRID_RERANK");
        verify(search).bm25Search("q", null, 10);
        verify(search, never()).keywordSearch(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        for (String k : List.of("BM25", "VECTOR", "HYBRID", "HYBRID_RERANK")) {
            assertThat(r.getSummary().get("platform").get(k).getRecallAtK()).as(k).isCloseTo(1.0, within(EPS));
        }
    }

    @Test
    void summaryIsBucketedByCategoryPlusAllBucket() {
        // platform: A(gt=1) 第 1 位命中；error_keyword: B(gt=2) 未命中
        stubAll("A", hits(1L), reranked(1L));
        stubAll("B", hits(9L), reranked(9L));

        EvaluationResultDTO r = service.evaluate(List.of(c("A", "platform", 1L), c("B", "error_keyword", 2L)));

        assertThat(r.getSummary().keySet()).containsExactlyInAnyOrder("platform", "error_keyword", "ALL");
        StrategyMetrics platformBm25 = r.getSummary().get("platform").get("BM25");
        assertThat(platformBm25.getSampleCount()).isEqualTo(1);
        assertThat(platformBm25.getRecallAtK()).isCloseTo(1.0, within(EPS));
        assertThat(platformBm25.getMrr()).isCloseTo(1.0, within(EPS));
        assertThat(r.getSummary().get("error_keyword").get("BM25").getRecallAtK()).isCloseTo(0.0, within(EPS));
        StrategyMetrics all = r.getSummary().get("ALL").get("BM25");
        assertThat(all.getSampleCount()).isEqualTo(2);
        assertThat(all.getRecallAtK()).isCloseTo(0.5, within(EPS));
        assertThat(all.getMrr()).isCloseTo(0.5, within(EPS));
        assertThat(r.getTotalCases()).isEqualTo(2);
        assertThat(r.getSkippedCases()).isZero();
    }

    @Test
    void categoryFallsBackToQueryTypeThenUnknown() {
        stubAll("A", hits(1L), reranked(1L));
        stubAll("B", hits(1L), reranked(1L));
        EvaluationCase legacy = EvaluationCase.builder().query("A").queryType("keyword")
                .groundTruth(List.of(GroundTruthItem.section("模块1,小节1"))).build();
        EvaluationCase none = EvaluationCase.builder().query("B")
                .groundTruth(List.of(GroundTruthItem.section("模块1,小节1"))).build();

        EvaluationResultDTO r = service.evaluate(List.of(legacy, none));

        assertThat(r.getSummary().keySet()).containsExactlyInAnyOrder("keyword", "unknown", "ALL");
        assertThat(detail(r, "A").getCategory()).isEqualTo("keyword");
        assertThat(detail(r, "B").getCategory()).isEqualTo("unknown");
    }

    @Test
    void casesWithoutGroundTruthAreSkippedNotScoredAsZero() {
        stubAll("A", hits(1L), reranked(1L));
        EvaluationCase empty = EvaluationCase.builder().query("E").category("platform")
                .groundTruth(List.of()).build();
        EvaluationCase nul = EvaluationCase.builder().query("N").category("platform").build();

        EvaluationResultDTO r = service.evaluate(List.of(c("A", "platform", 1L), empty, nul));

        assertThat(r.getSkippedCases()).isEqualTo(2);
        assertThat(r.getTotalCases()).isEqualTo(1);
        assertThat(r.getCases()).extracting(CaseDetail::getQuery).containsExactly("A");
        // 未被跳过时平均 Recall 会被拉低到 1/3；跳过后应为 1.0
        assertThat(r.getSummary().get("ALL").get("BM25").getRecallAtK()).isCloseTo(1.0, within(EPS));
        assertThat(r.getSummary().get("ALL").get("BM25").getSampleCount()).isEqualTo(1);
        // 被跳过的用例不应触发任何检索（尤其是收费的重排）
        verify(search, never()).bm25Search(eq("E"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(rerank, never()).rerank(eq("E"), anyList(), org.mockito.ArgumentMatchers.anyInt());
        verify(rerank, never()).rerank(eq("N"), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void detailKeepsTop10IdsAndFirstHitRank() {
        List<HybridSearchResult> twelve = hits(LongStream.rangeClosed(101, 112).boxed().toArray(Long[]::new));
        // 重排：目标 7 排第 3
        stubAll("q", twelve, reranked(50L, 51L, 7L));
        when(search.vectorSearch("q", null, 10)).thenReturn(hits(9L, 8L, 7L));
        when(search.bm25Search("q", null, 10)).thenReturn(hits(7L));

        EvaluationResultDTO r = service.evaluate(List.of(c("q", "platform", 7L)));

        CaseDetail d = detail(r, "q");
        assertThat(d.getGroundTruth()).containsExactly(7L);          // 明细里的 groundTruth 是解析后的 chunk_id
        assertThat(d.getExpectSections()).containsExactly("模块7,小节7");
        assertThat(d.getDocType()).isEqualTo("manual");
        CaseStrategyResult hybrid = d.getResults().get("HYBRID");
        assertThat(hybrid.getRetrievedChunkIds()).hasSize(10).startsWith(101L).endsWith(110L);
        assertThat(hybrid.getFirstHitRank()).isNull();
        assertThat(hybrid.getRecallAtK()).isCloseTo(0.0, within(EPS));
        assertThat(d.getResults().get("VECTOR").getRetrievedChunkIds()).containsExactly(9L, 8L, 7L);
        assertThat(d.getResults().get("VECTOR").getFirstHitRank()).isEqualTo(3);
        assertThat(d.getResults().get("VECTOR").getMrr()).isCloseTo(1.0 / 3, within(EPS));
        assertThat(d.getResults().get("BM25").getFirstHitRank()).isEqualTo(1);
        assertThat(d.getResults().get("HYBRID_RERANK").getRetrievedChunkIds()).containsExactly(50L, 51L, 7L);
        assertThat(d.getResults().get("HYBRID_RERANK").getFirstHitRank()).isEqualTo(3);
        assertThat(d.getResults().get("HYBRID_RERANK").isSuccess()).isTrue();
        assertThat(d.getResults().get("HYBRID_RERANK").getError()).isNull();
    }

    @Test
    void failedStrategyIsNotScoredAsZeroAndDoesNotAbort() {
        for (String q : List.of("A", "B")) {
            stubAll(q, hits(q.equals("A") ? 1L : 2L), reranked(q.equals("A") ? 1L : 2L));
        }
        when(rerank.rerank(eq("B"), anyList(), eq(10))).thenThrow(new RerankException("boom"));
        when(search.bm25Search("A", null, 10)).thenThrow(new IllegalStateException("db down"));

        EvaluationResultDTO r = service.evaluate(List.of(c("A", "platform", 1L), c("B", "platform", 2L)));

        StrategyMetrics rr = r.getSummary().get("platform").get("HYBRID_RERANK");
        assertThat(rr.getFailedCount()).isEqualTo(1);
        assertThat(rr.getSampleCount()).isEqualTo(1);
        assertThat(rr.getRecallAtK()).isCloseTo(1.0, within(EPS));   // 只有 A，B 的失败没被当 0 分
        StrategyMetrics bm = r.getSummary().get("platform").get("BM25");
        assertThat(bm.getFailedCount()).isEqualTo(1);
        assertThat(bm.getSampleCount()).isEqualTo(1);
        assertThat(r.getSummary().get("ALL").get("HYBRID_RERANK").getFailedCount()).isEqualTo(1);

        CaseStrategyResult failed = detail(r, "B").getResults().get("HYBRID_RERANK");
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getError()).contains("boom");
        assertThat(failed.getRecallAtK()).isNull();
        assertThat(failed.getMrr()).isNull();
        assertThat(failed.getRetrievedChunkIds()).isNull();
        assertThat(failed.getFirstHitRank()).isNull();

        assertThat(r.getFailures()).extracting(EvaluationResultDTO.FailureItem::getStrategy)
                .containsExactlyInAnyOrder("HYBRID_RERANK", "BM25");
        assertThat(r.getFailures()).filteredOn(f -> f.getStrategy().equals("BM25"))
                .extracting(EvaluationResultDTO.FailureItem::getQuery).containsExactly("A");
    }

    // ---------- 章节标注（expect_section）----------

    @Test
    void expectSectionIsResolvedToChunkIdsAndScoredAgainstThem() {
        stubAll("q", hits(3L, 9L), reranked(3L));

        EvaluationResultDTO r = service.evaluate(List.of(sec("q", "platform", "模块3,小节3")));

        CaseDetail d = detail(r, "q");
        assertThat(d.getGroundTruth()).containsExactly(3L);
        assertThat(d.getResults().get("VECTOR").getFirstHitRank()).isEqualTo(1);
        assertThat(d.getResults().get("VECTOR").getRecallAtK()).isCloseTo(1.0, within(EPS));
    }

    @Test
    void parentLevelSectionExpandsToAllChildChunks() {
        // 只标到“手册”这一级：命中语料里全部 9 个子块，召回 3 个里有 2 个命中 => 召回按“标准答案总数”算
        stubAll("q", hits(1L, 2L), reranked(1L));
        List<com.dataplatform.knowledge.dto.ChunkCandidate> two = new ArrayList<>();
        for (long i = 1; i <= 2; i++) {
            com.dataplatform.knowledge.dto.ChunkCandidate cc = new com.dataplatform.knowledge.dto.ChunkCandidate();
            cc.setChunkId(i);
            cc.setSectionPath("手册 / 同一章节 / 子节" + i);
            two.add(cc);
        }
        when(search.listAllChunkSections()).thenReturn(two);

        EvaluationResultDTO r = service.evaluate(List.of(sec("q", "platform", "同一章节")));

        assertThat(detail(r, "q").getGroundTruth()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(detail(r, "q").getResults().get("VECTOR").getRecallAtK()).isCloseTo(1.0, within(EPS));
    }

    @Test
    void multipleExpectSectionsAreUnioned() {
        stubAll("q", hits(1L), reranked(1L));

        EvaluationResultDTO r = service.evaluate(List.of(sec("q", "platform", "模块1,小节1", "模块2,小节2")));

        assertThat(detail(r, "q").getGroundTruth()).containsExactlyInAnyOrder(1L, 2L);
        // 只召回到其中一个 => Recall = 1/2
        assertThat(detail(r, "q").getResults().get("VECTOR").getRecallAtK()).isCloseTo(0.5, within(EPS));
    }

    @Test
    void sectionThatResolvesToNothingIsReportedAsUnresolvedNotScoredAsZero() {
        stubAll("ok", hits(1L), reranked(1L));
        stubAll("bad", hits(1L), reranked(1L));

        EvaluationResultDTO r = service.evaluate(List.of(
            sec("ok", "platform", "模块1,小节1"),
            sec("bad", "platform", "这一节文档里根本不存在")));

        // 解析不到任何 chunk：无法打分，计入 unresolved 并单独列出，绝不能当作 0 分拉低平均
        assertThat(r.getUnresolvedCases()).extracting(EvaluationResultDTO.UnresolvedItem::getQuery)
            .containsExactly("bad");
        assertThat(r.getUnresolvedCases().get(0).getExpectSections()).containsExactly("这一节文档里根本不存在");
        assertThat(r.getTotalCases()).isEqualTo(1);
        assertThat(r.getSummary().get("ALL").get("BM25").getSampleCount()).isEqualTo(1);
        assertThat(r.getSummary().get("ALL").get("BM25").getRecallAtK()).isCloseTo(1.0, within(EPS));
        verify(rerank, never()).rerank(eq("bad"), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void refusalMarkedCasesAreSkippedAndCountedSeparately() {
        stubAll("ok", hits(1L), reranked(1L));
        EvaluationCase refuse = EvaluationCase.builder().query("该拒答的题").category("colloquial")
            .groundTruth(List.of(GroundTruthItem.refusal())).build();

        EvaluationResultDTO r = service.evaluate(List.of(c("ok", "platform", 1L), refuse));

        assertThat(r.getRefusalCases()).isEqualTo(1);
        assertThat(r.getSkippedCases()).isZero();         // 拒答题不算“漏标注”
        assertThat(r.getTotalCases()).isEqualTo(1);
        verify(search, never()).bm25Search(eq("该拒答的题"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(rerank, never()).rerank(eq("该拒答的题"), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void completelyUnlabeledCaseIsStillSkippedAsBefore() {
        EvaluationCase blank = EvaluationCase.builder().query("没标注").category("platform")
            .groundTruth(List.of()).build();

        EvaluationResultDTO r = service.evaluate(List.of(blank));

        assertThat(r.getSkippedCases()).isEqualTo(1);
        assertThat(r.getRefusalCases()).isZero();
    }

    @Test
    void chunkCorpusIsLoadedOnceNotPerCase() {
        stubAll("a", hits(1L), reranked(1L));
        stubAll("b", hits(2L), reranked(2L));

        service.evaluate(List.of(c("a", "platform", 1L), c("b", "platform", 2L)));

        verify(search, org.mockito.Mockito.times(1)).listAllChunkSections();
    }

    @Test
    void failureToLoadChunkCorpusFailsTheWholeRunLoudly() {
        when(search.listAllChunkSections()).thenThrow(new IllegalStateException("pg down"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.evaluate(List.of(c("a", "platform", 1L))))
            .hasMessageContaining("pg down");
    }

    @Test
    void allFailedYieldsNullAveragesNotZero() {
        when(search.bm25Search("A", null, 10)).thenThrow(new IllegalStateException("x"));
        when(search.vectorSearch("A", null, 10)).thenThrow(new IllegalStateException("x"));
        when(search.hybridSearch("A", null, 10)).thenThrow(new IllegalStateException("x"));
        when(search.hybridSearch("A", null, 30)).thenThrow(new IllegalStateException("x"));

        EvaluationResultDTO r = service.evaluate(List.of(c("A", "platform", 1L)));

        StrategyMetrics m = r.getSummary().get("platform").get("BM25");
        assertThat(m.getSampleCount()).isZero();
        assertThat(m.getFailedCount()).isEqualTo(1);
        assertThat(m.getRecallAtK()).isNull();
        assertThat(m.getMrr()).isNull();
    }

    @Test
    void nullOrEmptyInputGivesEmptyResult() {
        EvaluationResultDTO r = service.evaluate(null);
        assertThat(r.getTotalCases()).isZero();
        assertThat(r.getCases()).isEmpty();
        assertThat(r.getFailures()).isEmpty();
        assertThat(r.getSummary().keySet()).containsExactly("ALL");
        assertThat(r.getSummary().get("ALL").get("BM25").getRecallAtK()).isNull();
    }
}
