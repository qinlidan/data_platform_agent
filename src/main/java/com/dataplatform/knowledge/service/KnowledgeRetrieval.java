package com.dataplatform.knowledge.service;

import com.dataplatform.entity.LowConfidenceQuestion;
import com.dataplatform.knowledge.confidence.ConfidenceGate;
import com.dataplatform.knowledge.confidence.EvidenceConfidence;
import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.mapper.LowConfidenceQuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库检索管线（不含大模型生成）：
 * 混合检索 → 向量相似度拒答 → 重排 → 重排分拒答 → 组装带编号的参考资料 → 保存引用。
 *
 * 两个调用方共用，保证拒答规则只有一份：
 * - {@link RAGService#answer}：拿结果再调一次大模型生成回答；
 * - queryFaq 工具：把参考资料交给外层 Agent 模型，由它组织回答（工具内不再套一层生成）。
 *
 * 故障策略：RerankException、检索异常是系统故障，原样抛出，不当作"知识库没答案"，也不写低置信度池。
 */
@Slf4j
@Service
public class KnowledgeRetrieval {

    static final int HYBRID_TOP_K = 30;
    static final int RERANK_TOP_N = 5;

    private static final int MAX_REASON_LENGTH = 500; // 对应表字段 VARCHAR(500)
    static final String SOURCE_RETRIEVAL_LOW_CONF = "retrieval_low_conf";

    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final PromptBuilder promptBuilder;
    private final LowConfidenceQuestionMapper lowConfidenceQuestionMapper;
    private final CitationStore citationStore;
    private final ConfidenceGate confidenceGate;

    public KnowledgeRetrieval(HybridSearchService hybridSearchService,
                              RerankService rerankService,
                              PromptBuilder promptBuilder,
                              LowConfidenceQuestionMapper lowConfidenceQuestionMapper,
                              CitationStore citationStore,
                              ConfidenceGate confidenceGate) {
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
        this.promptBuilder = promptBuilder;
        this.lowConfidenceQuestionMapper = lowConfidenceQuestionMapper;
        this.citationStore = citationStore;
        this.confidenceGate = confidenceGate;
    }

    /**
     * 检索结果。refused=true 时只有 refusalMessage 有意义；否则 prompt/references/topRelevance 有效。
     */
    public record Result(boolean refused, String refusalMessage, String prompt,
                         List<RerankResult> references, Double topRelevance) {

        static Result refused(String message) {
            return new Result(true, message, null, List.of(), null);
        }

        static Result hit(String prompt, List<RerankResult> references, Double topRelevance) {
            return new Result(false, null, prompt, references, topRelevance);
        }
    }

    /**
     * @param conversationId 会话标识：用于低置信度池归档和引用保存；为 null 则只跳过引用保存
     * @param vectorSimThreshold 首位候选 vectorSimilarity 小于该值拒答（等于通过）
     * @param rerankThreshold    首位 relevanceScore 小于该值拒答（等于通过）
     */
    public Result retrieve(String query, Long conversationId, double vectorSimThreshold, double rerankThreshold) {
        if (query == null || query.isBlank()) {
            return Result.refused(RAGService.REFUSAL_NO_INFO);
        }

        // 混合检索
        List<HybridSearchResult> candidates = hybridSearchService.hybridSearch(query, null, HYBRID_TOP_K);

        // 向量相似度拒答（score / rrfScore 只编码名次，不能用于阈值）
        if (candidates == null || candidates.isEmpty()) {
            log.warn("No recall for query: {}", query);
            recordLowConfidence(conversationId, query, "无召回结果");
            return Result.refused(RAGService.REFUSAL_NO_INFO);
        }
        Double topVectorSim = candidates.get(0).getVectorSimilarity();
        if (topVectorSim == null || topVectorSim < vectorSimThreshold) {
            String reason = topVectorSim == null
                ? String.format("首位候选无向量相似度，阈值 %.2f", vectorSimThreshold)
                : String.format("向量相似度过低: %.4f < 阈值 %.2f", topVectorSim, vectorSimThreshold);
            log.warn("Refuse by vector similarity, query: {}, {}", query, reason);
            recordLowConfidence(conversationId, query, reason);
            return Result.refused(RAGService.REFUSAL_NO_INFO);
        }

        // 重排（RerankException 不捕获）
        List<RerankResult> reranked = rerankService.rerank(query, candidates, RERANK_TOP_N);

        // 置信度闸检查
        EvidenceConfidence confidence = confidenceGate.evaluate(reranked);
        if (!confidence.getPassed()) {
            String reason = confidence.getRejectionReason();
            log.warn("Refuse by confidence gate, query: {}, {}", query, reason);
            recordLowConfidence(conversationId, query, reason);
            return Result.refused(RAGService.REFUSAL_LOW_RERANK);
        }

        String prompt = promptBuilder.buildPromptWithSearchResults(query, reranked);
        Double topRelevance = confidence.getTopRelevanceScore();
        return Result.hit(prompt, reranked, topRelevance);
    }

    /**
     * 保存引用（下标 0 对应回答里的 [1]，与 PromptBuilder 的编号一致）。
     * 不在 retrieve 里自动调用：RAGService 要等模型没有自评拒答才保存，避免覆盖上一次有效引用；
     * queryFaq 工具命中后则立即保存。
     */
    public void saveCitations(Long conversationId, List<RerankResult> reranked) {
        if (conversationId == null || reranked == null) {
            return;
        }
        List<CitationDetail> details = new ArrayList<>(reranked.size());
        for (int i = 0; i < reranked.size(); i++) {
            RerankResult r = reranked.get(i);
            details.add(CitationDetail.builder()
                .index(i + 1)
                .chunkId(r.getChunkId())
                .text(r.getText())
                .sectionPath(r.getSectionPath())
                .build());
        }
        citationStore.save(String.valueOf(conversationId), details);
    }

    /** 写低置信度池；失败只记日志，不影响返回给用户的结果。 */
    public void recordLowConfidence(Long conversationId, String question, String reason) {
        recordLowConfidence(conversationId, question, SOURCE_RETRIEVAL_LOW_CONF, reason);
    }

    public void recordLowConfidence(Long conversationId, String question, String source, String reason) {
        try {
            String r = reason != null && reason.length() > MAX_REASON_LENGTH
                ? reason.substring(0, MAX_REASON_LENGTH) : reason;
            lowConfidenceQuestionMapper.insert(LowConfidenceQuestion.builder()
                .conversationId(conversationId)
                .rawQuestion(question)
                .source(source)
                .reason(r)
                .build());
        } catch (Exception e) {
            log.error("Failed to record low confidence question: {}", question, e);
        }
    }
}
