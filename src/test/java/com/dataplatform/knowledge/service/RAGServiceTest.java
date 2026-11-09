package com.dataplatform.knowledge.service;

import com.dataplatform.entity.LowConfidenceQuestion;
import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.mapper.LowConfidenceQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RAGServiceTest {

    private static final String QUERY = "如何配置数据源？";
    private static final double VEC_T = 0.6;
    private static final double RERANK_T = 0.5;

    @Mock HybridSearchService hybridSearchService;
    @Mock RerankService rerankService;
    @Mock ChatClient.Builder chatClientBuilder;
    @Mock LowConfidenceQuestionMapper mapper;
    @Mock CitationStore citationStore;
    @Mock com.dataplatform.knowledge.confidence.ConfidenceGate confidenceGate;

    ChatClient chatClient;
    RAGService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        KnowledgeRetrieval retrieval = new KnowledgeRetrieval(hybridSearchService, rerankService,
            new PromptBuilder(), mapper, citationStore, confidenceGate);
        service = new RAGService(retrieval, chatClientBuilder);
    }

    // ---------- helpers ----------

    private static HybridSearchResult cand(long id, double vectorSim, double rrf) {
        return HybridSearchResult.builder()
            .chunkId(id).text("t" + id).rerankText("rt" + id).sectionPath("sec/" + id)
            .score(rrf).rrfScore(rrf).vectorSimilarity(vectorSim).build();
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

    private void givenLlmAnswer(String answer) {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(answer);
    }

    private LowConfidenceQuestion captureInsertedQuestion() {
        ArgumentCaptor<LowConfidenceQuestion> cap = ArgumentCaptor.forClass(LowConfidenceQuestion.class);
        verify(mapper, times(1)).insert(cap.capture());
        return cap.getValue();
    }

    // ---------- 向量相似度拒答 ----------

    @Test
    void vectorSimilarity059Refuses() {
        givenCandidates(cand(1, 0.59, 0.03));

        String result = service.answer(QUERY, 7L, VEC_T, RERANK_T);

        assertEquals(RAGService.REFUSAL_NO_INFO, result);
        verifyNoInteractions(rerankService);
        verify(chatClientBuilder, never()).build();
        LowConfidenceQuestion q = captureInsertedQuestion();
        assertEquals("retrieval_low_conf", q.getSource());
        assertEquals(7L, q.getConversationId());
        assertEquals(QUERY, q.getRawQuestion());
        assertTrue(q.getReason().contains("向量相似度"), q.getReason());
        assertTrue(q.getReason().contains("0.59"), q.getReason());
        verify(citationStore, never()).save(any(), any());
    }

    @Test
    void vectorSimilarity060Passes() {
        givenCandidates(cand(1, 0.60, 0.03));
        givenRerank(rr(1, 0.9));
        givenLlmAnswer("答案 [1]");

        String result = service.answer(QUERY, 7L, VEC_T, RERANK_T);

        assertEquals("答案 [1]", result);
        verify(rerankService).rerank(eq(QUERY), anyList(), eq(5));
        verifyNoInteractions(mapper);
    }

    @Test
    void refusalUsesVectorSimilarityNotRrfScore() {
        // rrfScore 很小(0.016)但 vectorSimilarity 很高：必须通过
        givenCandidates(cand(1, 0.92, 0.016));
        givenRerank(rr(1, 0.9));
        givenLlmAnswer("答案 [1]");

        assertEquals("答案 [1]", service.answer(QUERY, 7L, VEC_T, RERANK_T));
        verifyNoInteractions(mapper);
    }

    @Test
    void highRrfButLowVectorSimilarityStillRefuses() {
        // 反向：rrf/score 看似很高(0.9)，向量相似度低：必须拒答
        givenCandidates(cand(1, 0.30, 0.9));

        assertEquals(RAGService.REFUSAL_NO_INFO, service.answer(QUERY, 7L, VEC_T, RERANK_T));
        verifyNoInteractions(rerankService);
        assertEquals("retrieval_low_conf", captureInsertedQuestion().getSource());
    }

    @Test
    void nullVectorSimilarityRefuses() {
        HybridSearchResult c = cand(1, 0.9, 0.03);
        c.setVectorSimilarity(null);
        givenCandidates(c);

        assertEquals(RAGService.REFUSAL_NO_INFO, service.answer(QUERY, 7L, VEC_T, RERANK_T));
        verifyNoInteractions(rerankService);
        assertEquals("retrieval_low_conf", captureInsertedQuestion().getSource());
    }

    @Test
    void noRecallRefusesAndRecords() {
        when(hybridSearchService.hybridSearch(eq(QUERY), isNull(), eq(30))).thenReturn(List.of());

        assertEquals(RAGService.REFUSAL_NO_INFO, service.answer(QUERY, 7L, VEC_T, RERANK_T));

        verifyNoInteractions(rerankService);
        LowConfidenceQuestion q = captureInsertedQuestion();
        assertEquals("retrieval_low_conf", q.getSource());
        assertTrue(q.getReason().contains("无召回"), q.getReason());
        verify(citationStore, never()).save(any(), any());
    }

    // ---------- 重排分拒答 ----------

    @Test
    void lowRerankScoreRefusesAndRecords() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.49));

        String result = service.answer(QUERY, 7L, VEC_T, RERANK_T);

        assertEquals(RAGService.REFUSAL_LOW_RERANK, result);
        verify(chatClientBuilder, never()).build();
        LowConfidenceQuestion q = captureInsertedQuestion();
        assertEquals("retrieval_low_conf", q.getSource());
        assertTrue(q.getReason().contains("重排"), q.getReason());
        assertTrue(q.getReason().contains("0.49"), q.getReason());
        verify(citationStore, never()).save(any(), any());
    }

    @Test
    void rerankScoreEqualToThresholdPasses() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.5));
        givenLlmAnswer("答案 [1]");

        assertEquals("答案 [1]", service.answer(QUERY, 7L, VEC_T, RERANK_T));
        verifyNoInteractions(mapper);
    }

    @Test
    void emptyRerankResultRefusesAndRecords() {
        givenCandidates(cand(1, 0.8, 0.03));
        when(rerankService.rerank(eq(QUERY), anyList(), eq(5))).thenReturn(List.of());

        assertEquals(RAGService.REFUSAL_LOW_RERANK, service.answer(QUERY, 7L, VEC_T, RERANK_T));
        assertEquals("retrieval_low_conf", captureInsertedQuestion().getSource());
    }

    // ---------- 模型自己拒答 ----------

    @Test
    void modelSelfRefusalRecordsSelfCheckAndKeepsPreviousCitations() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.9));
        givenLlmAnswer("抱歉，知识库中没有相关信息");

        String result = service.answer(QUERY, 7L, VEC_T, RERANK_T);

        assertEquals("抱歉，知识库中没有相关信息", result);
        LowConfidenceQuestion q = captureInsertedQuestion();
        assertEquals("self_check", q.getSource());
        verify(citationStore, never()).save(any(), any());
    }

    @Test
    void modelSaysCannotAnswerAlsoSelfCheck() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.9));
        givenLlmAnswer("抱歉，我无法回答这个问题。");

        service.answer(QUERY, 7L, VEC_T, RERANK_T);

        assertEquals("self_check", captureInsertedQuestion().getSource());
    }

    @Test
    void longNormalAnswerMentioningPhraseIsNotTreatedAsRefusal() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.9));
        String longAnswer = "配置数据源需要先填写连接信息并测试连通性，保存后即可在作业中选择该数据源使用 [1]。"
            + "如果某个字段在文档里写着无法回答的情形，则需要联系管理员确认 [2]。";
        givenLlmAnswer(longAnswer);

        assertEquals(longAnswer, service.answer(QUERY, 7L, VEC_T, RERANK_T));
        verifyNoInteractions(mapper);
        verify(citationStore).save(eq("7"), anyList());
    }

    // ---------- 写库失败 ----------

    @Test
    void mapperFailureDoesNotAffectRefusalResult() {
        givenCandidates(cand(1, 0.1, 0.03));
        when(mapper.insert(any(LowConfidenceQuestion.class))).thenThrow(new RuntimeException("db down"));

        String result = assertDoesNotThrow(() -> service.answer(QUERY, 7L, VEC_T, RERANK_T));

        assertEquals(RAGService.REFUSAL_NO_INFO, result);
    }

    @Test
    void mapperFailureDoesNotAffectSelfCheckResult() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.9));
        givenLlmAnswer("抱歉，知识库中没有相关信息");
        when(mapper.insert(any(LowConfidenceQuestion.class))).thenThrow(new RuntimeException("db down"));

        assertEquals("抱歉，知识库中没有相关信息", service.answer(QUERY, 7L, VEC_T, RERANK_T));
    }

    // ---------- RerankException ----------

    @Test
    void rerankExceptionPropagatesAndDoesNotPolluteLowConfidencePool() {
        givenCandidates(cand(1, 0.8, 0.03));
        when(rerankService.rerank(any(), anyList(), anyInt()))
            .thenThrow(new RerankService.RerankException("boom"));

        assertThrows(RerankService.RerankException.class,
            () -> service.answer(QUERY, 7L, VEC_T, RERANK_T));

        verifyNoInteractions(mapper);
        verify(citationStore, never()).save(any(), any());
        verify(chatClientBuilder, never()).build();
    }

    // ---------- 引用保存 ----------

    @Test
    void successSavesCitationsWithCorrectKeyAndOrder() {
        givenCandidates(cand(11, 0.8, 0.03), cand(22, 0.7, 0.02), cand(33, 0.7, 0.01));
        givenRerank(rr(22, 0.95), rr(11, 0.80), rr(33, 0.60));
        givenLlmAnswer("答案 [1][2][3]");

        service.answer(QUERY, 42L, VEC_T, RERANK_T);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CitationDetail>> cap = ArgumentCaptor.forClass(List.class);
        verify(citationStore).save(eq("42"), cap.capture());
        List<CitationDetail> saved = cap.getValue();
        assertEquals(3, saved.size());
        // 下标0 = [1] = 重排后的第一名
        assertEquals(1, saved.get(0).getIndex());
        assertEquals(22L, saved.get(0).getChunkId());
        assertEquals("rt22", saved.get(0).getText());
        assertEquals("sec/22", saved.get(0).getSectionPath());
        assertEquals(2, saved.get(1).getIndex());
        assertEquals(11L, saved.get(1).getChunkId());
        assertEquals(3, saved.get(2).getIndex());
        assertEquals(33L, saved.get(2).getChunkId());
        verifyNoInteractions(mapper);
    }

    @Test
    void promptNumberingMatchesSavedCitations() {
        givenCandidates(cand(11, 0.8, 0.03), cand(22, 0.7, 0.02));
        givenRerank(rr(22, 0.95), rr(11, 0.80));
        givenLlmAnswer("答案 [1]");

        service.answer(QUERY, 42L, VEC_T, RERANK_T);

        ArgumentCaptor<String> userCap = ArgumentCaptor.forClass(String.class);
        // 注意：givenLlmAnswer 的 stub 过程也会调用一次 user(...)，所以取最后一次（即 RAGService 的真实调用）
        verify(chatClient.prompt().system(anyString()), atLeastOnce()).user(userCap.capture());
        String prompt = userCap.getAllValues().get(userCap.getAllValues().size() - 1);
        assertTrue(prompt.contains("[参考资料 1]\nrt22\n"), prompt);
        assertTrue(prompt.contains("[参考资料 2]\nrt11\n"), prompt);
    }

    @Test
    void nullConversationIdDoesNotCrashAndSkipsSave() {
        givenCandidates(cand(1, 0.8, 0.03));
        givenRerank(rr(1, 0.9));
        givenLlmAnswer("答案 [1]");

        assertEquals("答案 [1]", service.answer(QUERY, null, VEC_T, RERANK_T));
        verify(citationStore, never()).save(any(), any());
    }

    @Test
    void nullConversationIdRefusalStillRecordsWithNullConversation() {
        givenCandidates(cand(1, 0.1, 0.03));

        assertEquals(RAGService.REFUSAL_NO_INFO, service.answer(QUERY, null, VEC_T, RERANK_T));

        LowConfidenceQuestion q = captureInsertedQuestion();
        assertNull(q.getConversationId());
        assertEquals("retrieval_low_conf", q.getSource());
    }
}
