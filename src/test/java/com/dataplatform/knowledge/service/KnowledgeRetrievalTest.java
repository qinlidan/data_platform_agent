package com.dataplatform.knowledge.service;

import com.dataplatform.entity.LowConfidenceQuestion;
import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.RerankService.RerankException;
import com.dataplatform.mapper.LowConfidenceQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 检索段（不含大模型生成）：供 queryFaq 工具使用，由外层 Agent 模型负责组织回答。
 * 拒答规则、低置信度池、引用保存必须与 RAGService.answer 保持一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeRetrievalTest {

    private static final String QUERY = "Spark 3.2 内存溢出";

    @Mock HybridSearchService hybridSearchService;
    @Mock RerankService rerankService;
    @Mock LowConfidenceQuestionMapper mapper;
    @Mock CitationStore citationStore;

    KnowledgeRetrieval retrieval;

    @BeforeEach
    void setUp() {
        retrieval = new KnowledgeRetrieval(hybridSearchService, rerankService, new PromptBuilder(),
            mapper, citationStore);
    }

    private static HybridSearchResult cand(long id, double vectorSim) {
        return HybridSearchResult.builder().chunkId(id).text("t" + id).rerankText("rt" + id)
            .sectionPath("sec/" + id).rrfScore(0.03).score(0.03).vectorSimilarity(vectorSim).build();
    }

    private static RerankResult rr(long id, double relevance) {
        return RerankResult.builder().chunkId(id).text("rt" + id).sectionPath("sec/" + id)
            .relevanceScore(relevance).vectorSimilarity(0.8).build();
    }

    private void givenCandidates(HybridSearchResult... cs) {
        when(hybridSearchService.hybridSearch(eq(QUERY), isNull(), eq(30))).thenReturn(List.of(cs));
    }

    private void givenRerank(RerankResult... rs) {
        when(rerankService.rerank(eq(QUERY), anyList(), eq(5))).thenReturn(List.of(rs));
    }

    // ---------- 命中 ----------

    @Test
    void hitReturnsPromptWithNumberedReferencesButDoesNotSaveByItself() {
        givenCandidates(cand(1, 0.8), cand(2, 0.7));
        givenRerank(rr(2, 0.9), rr(1, 0.6));

        KnowledgeRetrieval.Result r = retrieval.retrieve(QUERY, 7L, 0.6, 0.5);

        assertFalse(r.refused());
        assertNull(r.refusalMessage());
        assertEquals(List.of(2L, 1L), r.references().stream().map(RerankResult::getChunkId).toList());
        // 提示词里的编号与引用顺序一致：[1] 是重排第一名
        assertTrue(r.prompt().contains("[参考资料 1]\nrt2"), r.prompt());
        assertTrue(r.prompt().contains("[参考资料 2]\nrt1"), r.prompt());
        // 保存时机由调用方决定（RAGService 要等模型没自评拒答才存），所以检索本身不保存
        verify(citationStore, never()).save(any(), any());
        verify(mapper, never()).insert(any());
    }

    @Test
    void saveCitationsStoresNumberedReferencesUnderConversationKey() {
        retrieval.saveCitations(7L, List.of(rr(2, 0.9), rr(1, 0.6)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CitationDetail>> cap = ArgumentCaptor.forClass(List.class);
        verify(citationStore).save(eq("7"), cap.capture());
        assertEquals(1, cap.getValue().get(0).getIndex());
        assertEquals(2L, cap.getValue().get(0).getChunkId());
        assertEquals("sec/2", cap.getValue().get(0).getSectionPath());
        assertEquals(2, cap.getValue().get(1).getIndex());
    }

    @Test
    void saveCitationsWithNullConversationIdIsNoop() {
        retrieval.saveCitations(null, List.of(rr(1, 0.9)));
        verify(citationStore, never()).save(any(), any());
    }

    // ---------- 拒答：与 RAGService 同一规则 ----------

    @Test
    void vectorSimilarityBelowThresholdRefusesAndRecords() {
        givenCandidates(cand(1, 0.59));

        KnowledgeRetrieval.Result r = retrieval.retrieve(QUERY, 7L, 0.6, 0.5);

        assertTrue(r.refused());
        assertEquals(RAGService.REFUSAL_NO_INFO, r.refusalMessage());
        verifyNoInteractions(rerankService);
        verify(citationStore, never()).save(any(), any());
        ArgumentCaptor<LowConfidenceQuestion> cap = ArgumentCaptor.forClass(LowConfidenceQuestion.class);
        verify(mapper, times(1)).insert(cap.capture());
        assertEquals("retrieval_low_conf", cap.getValue().getSource());
        assertEquals(7L, cap.getValue().getConversationId());
        assertEquals(QUERY, cap.getValue().getRawQuestion());
    }

    @Test
    void vectorSimilarityEqualToThresholdPasses() {
        givenCandidates(cand(1, 0.60));
        givenRerank(rr(1, 0.9));
        assertFalse(retrieval.retrieve(QUERY, 7L, 0.6, 0.5).refused());
    }

    @Test
    void noRecallRefusesAndRecords() {
        givenCandidates();
        KnowledgeRetrieval.Result r = retrieval.retrieve(QUERY, 7L, 0.6, 0.5);
        assertTrue(r.refused());
        verify(mapper, times(1)).insert(any());
    }

    @Test
    void lowRerankScoreRefusesAndRecords() {
        givenCandidates(cand(1, 0.8));
        givenRerank(rr(1, 0.49));

        KnowledgeRetrieval.Result r = retrieval.retrieve(QUERY, 7L, 0.6, 0.5);

        assertTrue(r.refused());
        assertEquals(RAGService.REFUSAL_LOW_RERANK, r.refusalMessage());
        verify(citationStore, never()).save(any(), any());
        verify(mapper, times(1)).insert(any());
    }

    @Test
    void rerankScoreEqualToThresholdPasses() {
        givenCandidates(cand(1, 0.8));
        givenRerank(rr(1, 0.5));
        assertFalse(retrieval.retrieve(QUERY, 7L, 0.6, 0.5).refused());
    }

    // ---------- 故障与边界 ----------

    @Test
    void rerankFailurePropagatesAndDoesNotPolluteLowConfidencePool() {
        givenCandidates(cand(1, 0.8));
        when(rerankService.rerank(eq(QUERY), anyList(), eq(5))).thenThrow(new RerankException("boom"));

        assertThrows(RerankException.class, () -> retrieval.retrieve(QUERY, 7L, 0.6, 0.5));
        verify(mapper, never()).insert(any());
    }

    @Test
    void mapperFailureDoesNotBreakRefusal() {
        givenCandidates(cand(1, 0.1));
        when(mapper.insert(any(LowConfidenceQuestion.class))).thenThrow(new RuntimeException("db down"));

        KnowledgeRetrieval.Result r = retrieval.retrieve(QUERY, 7L, 0.6, 0.5);
        assertTrue(r.refused());
    }

    @Test
    void nullConversationIdStillReturnsReferences() {
        givenCandidates(cand(1, 0.8));
        givenRerank(rr(1, 0.9));

        KnowledgeRetrieval.Result r = retrieval.retrieve(QUERY, null, 0.6, 0.5);

        assertFalse(r.refused());
        assertEquals(1, r.references().size());
    }

    @Test
    void blankQueryRefusesWithoutCallingAnyBackend() {
        KnowledgeRetrieval.Result r = retrieval.retrieve("  ", 7L, 0.6, 0.5);
        assertTrue(r.refused());
        verifyNoInteractions(hybridSearchService, rerankService);
    }
}
