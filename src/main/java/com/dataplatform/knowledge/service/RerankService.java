package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 重排服务：调用 DashScope 文本重排接口。
 *
 * 接口形态已用真实账号验证（2026-11）：
 * - 端点 /api/v1/services/rerank/text-rerank/text-rerank
 * - 请求 {model, input:{query, documents:[String]}, parameters:{top_n, return_documents}}
 * - 响应 output.results[].{index, relevance_score}
 * - 默认模型 qwen3.7-text-rerank（已用真实账号验证 200）；旧模型 gte-rerank 在当前账号下返回 403，不可用。
 *
 * 失败策略：重排失败必须抛出，不得静默退回原顺序。
 * 否则"混合+重排"会悄悄退化成"混合"，评估数据和线上拒答都会失真。
 */
@Service
@Slf4j
public class RerankService {

    private static final String DEFAULT_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String url;
    private final String model;

    @Autowired
    public RerankService(
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Value("${knowledge.rerank.url:" + DEFAULT_URL + "}") String url,
            @Value("${knowledge.rerank.model:qwen3.7-text-rerank}") String model) {
        this(new RestTemplate(), apiKey, url, model);
    }

    /** 测试用：注入 RestTemplate */
    RerankService(RestTemplate restTemplate, String apiKey, String url, String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.url = url;
        this.model = model;
    }

    /**
     * @param candidates 混合检索的候选，文档文本取 rerankText，缺失退回 text
     * @param topN       返回条数上限
     * @return 按 relevanceScore 降序
     * @throws RerankException 调用失败、响应缺失或 index 越界
     */
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        // 没有任何文本的候选（例如存量数据尚未回填 text/rerank_text）发给接口会让整个请求 400，
        // 发送前剔除；返回的 index 是"发送列表"的下标，所以后面按发送列表而不是原列表还原。
        List<HybridSearchResult> sendable = new ArrayList<>(candidates.size());
        List<String> documents = new ArrayList<>(candidates.size());
        for (HybridSearchResult c : candidates) {
            String doc = documentOf(c);
            if (!doc.isBlank()) {
                sendable.add(c);
                documents.add(doc);
            }
        }
        if (sendable.size() < candidates.size()) {
            log.warn("重排前剔除 {} 条无文本候选（text/rerank_text 均为空，可能尚未回填）",
                candidates.size() - sendable.size());
        }
        if (sendable.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> body = Map.of(
            "model", model,
            "input", Map.of("query", query, "documents", documents),
            "parameters", Map.of(
                "top_n", Math.min(Math.max(topN, 1), documents.size()),
                "return_documents", false));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<?, ?> response;
        try {
            response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
        } catch (Exception e) {
            throw new RerankException("重排接口调用失败: " + e.getMessage(), e);
        }
        return parse(response, sendable);
    }

    private static String documentOf(HybridSearchResult c) {
        String t = c.getRerankText() != null && !c.getRerankText().isBlank() ? c.getRerankText() : c.getText();
        return t == null ? "" : t;
    }

    private static List<RerankResult> parse(Map<?, ?> response, List<HybridSearchResult> candidates) {
        if (response == null || !(response.get("output") instanceof Map<?, ?> output)
                || !(output.get("results") instanceof List<?> results)) {
            throw new RerankException("重排响应缺少 output.results: " + response);
        }
        List<RerankResult> out = new ArrayList<>(results.size());
        for (Object o : results) {
            if (!(o instanceof Map<?, ?> r) || !(r.get("index") instanceof Number idx)
                    || !(r.get("relevance_score") instanceof Number score)) {
                throw new RerankException("重排响应条目格式异常: " + o);
            }
            int i = idx.intValue();
            if (i < 0 || i >= candidates.size()) {
                // Review Focus #4：服务端返回越界 index 时明确失败，而不是 IndexOutOfBounds 或错位映射
                throw new RerankException("重排返回越界 index=" + i + "，候选数=" + candidates.size());
            }
            HybridSearchResult src = candidates.get(i);
            out.add(RerankResult.builder()
                .chunkId(src.getChunkId())
                .text(documentOf(src))
                .sectionPath(src.getSectionPath())
                .relevanceScore(score.doubleValue())
                .vectorSimilarity(src.getVectorSimilarity())
                .build());
        }
        out.sort(Comparator.comparing(RerankResult::getRelevanceScore).reversed());
        return out;
    }

    public static class RerankException extends RuntimeException {
        public RerankException(String message) {
            super(message);
        }

        public RerankException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
