package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.Bm25Rows;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.pgmapper.HybridSearchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** bm25Search 的编排逻辑：mapper 全部 mock，数值与 Bm25ScorerTest 的手算例子对照。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HybridSearchServiceBm25Test {

    @Mock private HybridSearchMapper mapper;
    @Mock private EmbeddingService embeddingService;
    @InjectMocks private HybridSearchService service;



    private final AtomicLong nowMillis = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() {
        service.setClock(new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(nowMillis.get()); }
        });
        service.setBm25StatsTtlSeconds(60);
    }

    private static Bm25Rows.Candidate cand(long id, int len) {
        Bm25Rows.Candidate c = new Bm25Rows.Candidate();
        c.setChunkId(id);
        c.setDocType("dt");
        c.setRouteAction("tunable");
        c.setText("text" + id);
        c.setRerankText("rerank" + id);
        c.setSectionPath("path" + id);
        c.setDocLength(len);
        return c;
    }

    private static Bm25Rows.TermFreq tf(long id, String term, int n) {
        Bm25Rows.TermFreq t = new Bm25Rows.TermFreq();
        t.setChunkId(id);
        t.setLexeme(term);
        t.setTf(n);
        return t;
    }


    private static Bm25Rows.TermDocFreq df(String term, long n) {
        Bm25Rows.TermDocFreq d = new Bm25Rows.TermDocFreq();
        d.setLexeme(term);
        d.setDocFreq(n);
        return d;
    }

    private static Bm25Rows.CorpusStats stats(long n, double avg) {
        Bm25Rows.CorpusStats s = new Bm25Rows.CorpusStats();
        s.setTotalDocs(n);
        s.setAvgDocLength(avg);
        return s;
    }

    /** 与 Bm25ScorerTest 手算例子一致：d1 = 1.009883，d2 = 2.208026 */
    private void stubHandComputedExample(String docType) {
        when(mapper.queryTerms("spark hive")).thenReturn(List.of("spark", "hive"));
        when(mapper.corpusStats()).thenReturn(stats(4, 5.0));
        when(mapper.docFrequencies(List.of("spark", "hive"))).thenReturn(List.of(df("spark", 2), df("hive", 1)));
        when(mapper.bm25Candidates(List.of("spark", "hive"), docType, 200))
                .thenReturn(new ArrayList<>(List.of(cand(1, 4), cand(2, 6))));
        when(mapper.termFrequencies(any(), eq(List.of("spark", "hive")))).thenReturn(List.of(
                tf(1, "spark", 2), tf(2, "spark", 1), tf(2, "hive", 2)));
    }

    @Test
    void ranksCandidatesByBm25AndFillsResultFields() {
        stubHandComputedExample(null);

        List<HybridSearchResult> rs = service.bm25Search("spark hive", null, 10);

        assertThat(rs).extracting(HybridSearchResult::getChunkId).containsExactly(2L, 1L);
        assertThat(rs.get(0).getScore()).isCloseTo(2.208026, within(1e-5));
        assertThat(rs.get(1).getScore()).isCloseTo(1.009883, within(1e-5));
        assertThat(rs.get(0).getKeywordRank()).isEqualTo(1);
        assertThat(rs.get(1).getKeywordRank()).isEqualTo(2);
        assertThat(rs.get(0).getText()).isEqualTo("text2");
        assertThat(rs.get(0).getRerankText()).isEqualTo("rerank2");
        assertThat(rs.get(0).getSectionPath()).isEqualTo("path2");
        assertThat(rs.get(0).getDocType()).isEqualTo("dt");
        verify(mapper).termFrequencies(List.of(1L, 2L), List.of("spark", "hive"));
    }

    @Test
    void truncatesToTopKAndDropsZeroScoreCandidates() {
        stubHandComputedExample("dt");
        // 第三个候选没有任何词频行 -> 得分 0，必须丢弃
        when(mapper.bm25Candidates(List.of("spark", "hive"), "dt", 200))
                .thenReturn(new ArrayList<>(List.of(cand(1, 4), cand(2, 6), cand(3, 5))));

        assertThat(service.bm25Search("spark hive", "dt", 10))
                .extracting(HybridSearchResult::getChunkId).containsExactly(2L, 1L);
        assertThat(service.bm25Search("spark hive", "dt", 1))
                .extracting(HybridSearchResult::getChunkId).containsExactly(2L);
    }

    @Test
    void blankQueryOrNoTermsReturnsEmptyWithoutTouchingDb() {
        assertThat(service.bm25Search("  ", null, 5)).isEmpty();
        assertThat(service.bm25Search(null, null, 5)).isEmpty();
        when(mapper.queryTerms("的")).thenReturn(List.of());
        assertThat(service.bm25Search("的", null, 5)).isEmpty();
        verify(mapper, never()).bm25Candidates(any(), any(), anyInt());
        verify(mapper, never()).corpusStats();
    }

    @Test
    void emptyCorpusReturnsEmpty() {
        when(mapper.queryTerms("spark")).thenReturn(List.of("spark"));
        when(mapper.corpusStats()).thenReturn(stats(0, 0));
        assertThat(service.bm25Search("spark", null, 5)).isEmpty();
        verify(mapper, never()).bm25Candidates(any(), any(), anyInt());
    }

    @Test
    void corpusStatsAreCachedWithinTtlAndRefreshedAfterExpiryOrInvalidate() {
        stubHandComputedExample(null);

        service.bm25Search("spark hive", null, 5);
        nowMillis.addAndGet(59_000);
        service.bm25Search("spark hive", null, 5);
        verify(mapper, times(1)).corpusStats();        // TTL 内命中缓存

        nowMillis.addAndGet(2_000);                    // 共 61s，已过期
        service.bm25Search("spark hive", null, 5);
        verify(mapper, times(2)).corpusStats();

        service.invalidateBm25Stats();                 // 入库后可主动失效
        service.bm25Search("spark hive", null, 5);
        verify(mapper, times(3)).corpusStats();
    }

    @Test
    void candidateQueryIsCappedAt200AndPassesDocType() {
        stubHandComputedExample("dt");
        service.bm25Search("spark hive", "dt", 3);
        verify(mapper).bm25Candidates(eq(List.of("spark", "hive")), eq("dt"), eq(200));
    }

    @Test
    void blankDocTypeIsTreatedAsNoFilter() {
        stubHandComputedExample(null);
        service.bm25Search("spark hive", "  ", 3);
        verify(mapper).bm25Candidates(eq(List.of("spark", "hive")), eq(null), eq(200));
    }

    @Test
    void searchCandidatesHandlesBlankKeywordAndClampsLimit() {
        assertThat(service.searchCandidates("  ", 20)).isEmpty();
        assertThat(service.searchCandidates(null, 20)).isEmpty();
        verify(mapper, never()).searchCandidates(any(), anyInt());

        service.searchCandidates("spark", 100000);
        verify(mapper).searchCandidates("spark", 100);
        service.searchCandidates("spark", 0);
        verify(mapper).searchCandidates("spark", 1);
    }
}
