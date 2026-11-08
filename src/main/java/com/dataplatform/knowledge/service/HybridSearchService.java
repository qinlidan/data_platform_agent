package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.Bm25Rows;
import com.dataplatform.knowledge.dto.ChunkCandidate;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.pgmapper.HybridSearchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索服务（向量 + 关键词 + RRF 融合）
 *
 * Task 3 实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final HybridSearchMapper hybridSearchMapper;
    private final EmbeddingService embeddingService;

    /**
     * 混合检索（向量 + 关键词 + RRF 融合）
     *
     * @param queryText 查询文本
     * @param docType 文档类型过滤（null表示不过滤）
     * @param topK 返回top-k结果数量
     * @return RRF 融合后的检索结果列表
     */
    public List<HybridSearchResult> hybridSearch(String queryText, String docType, int topK) {
        log.info("混合检索 - queryText: {}, docType: {}, topK: {}", queryText, docType, topK);

        // 1. 生成查询向量
        float[] queryVector = embeddingService.embedQuery(queryText);
        String queryVectorStr = embeddingService.formatVector(queryVector);

        // 2. RRF 融合检索
        List<HybridSearchResult> results = hybridSearchMapper.hybridSearch(
                queryVectorStr,
                queryText,
                docType,
                topK
        );

        log.info("混合检索完成 - 返回 {} 条结果", results.size());
        return results;
    }

    /**
     * 纯向量检索（用于评估对比）
     *
     * @param queryText 查询文本
     * @param docType 文档类型过滤
     * @param topK 返回top-k结果数量
     * @return 向量检索结果列表
     */
    public List<HybridSearchResult> vectorSearch(String queryText, String docType, int topK) {
        log.info("纯向量检索 - queryText: {}, docType: {}, topK: {}", queryText, docType, topK);

        float[] queryVector = embeddingService.embedQuery(queryText);
        String queryVectorStr = embeddingService.formatVector(queryVector);

        List<HybridSearchResult> results = hybridSearchMapper.vectorSearch(
                queryVectorStr,
                docType,
                topK
        );

        log.info("向量检索完成 - 返回 {} 条结果", results.size());
        return results;
    }

    /**
     * 纯关键词检索（用于评估对比）
     *
     * @param queryText 查询文本
     * @param docType 文档类型过滤
     * @param topK 返回top-k结果数量
     * @return 关键词检索结果列表
     */
    public List<HybridSearchResult> keywordSearch(String queryText, String docType, int topK) {
        log.info("纯关键词检索 - queryText: {}, docType: {}, topK: {}", queryText, docType, topK);

        List<HybridSearchResult> results = hybridSearchMapper.keywordSearch(
                queryText,
                docType,
                topK
        );

        log.info("关键词检索完成 - 返回 {} 条结果", results.size());
        return results;
    }

    // ======================= BM25 路 =======================

    /** BM25 候选上限：先用 GIN 索引召回最多这么多条，再在应用层重排。 */
    static final int BM25_CANDIDATE_LIMIT = 200;
    /** 人工标注候选接口的 limit 上限 */
    static final int CANDIDATE_MAX_LIMIT = 100;

    private final Bm25Scorer bm25Scorer = new Bm25Scorer();

    /** 库内统计（N、avgdl）的缓存有效期。入库不会主动通知本服务，过期后自动重新统计。 */
    @Value("${knowledge.bm25.stats-ttl-seconds:60}")
    private long bm25StatsTtlSeconds = 60;

    private Clock clock = Clock.systemUTC();
    private volatile CachedStats cachedStats;

    private record CachedStats(Bm25Rows.CorpusStats stats, long loadedAtMillis) {
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    void setBm25StatsTtlSeconds(long seconds) {
        this.bm25StatsTtlSeconds = seconds;
    }

    /** 立即丢弃 N / avgdl 缓存（批量入库或删除后可调用；否则最多滞后 TTL 秒）。 */
    public void invalidateBm25Stats() {
        this.cachedStats = null;
    }

    private Bm25Rows.CorpusStats corpusStats() {
        long now = clock.millis();
        CachedStats c = cachedStats;
        if (c != null && now - c.loadedAtMillis() < bm25StatsTtlSeconds * 1000L) {
            return c.stats();
        }
        Bm25Rows.CorpusStats fresh = hybridSearchMapper.corpusStats();
        cachedStats = new CachedStats(fresh, now);
        return fresh;
    }

    /**
     * BM25 检索（Okapi BM25，k1=1.2，b=0.75）。
     *
     * 流程：jieba 分词 → GIN 索引 OR 匹配取最多 200 个候选 → 取候选词频 → 应用层 BM25 打分 → 取前 topK。
     * 与 {@link #keywordSearch} 的区别：keywordSearch 是 AND 语义 + ts_rank_cd；这里是 OR 语义 + 真正的 BM25。
     * 无任何词项命中的候选（得分 0）不会返回。结果的 score 为 BM25 分，keywordRank 为名次。
     */
    public List<HybridSearchResult> bm25Search(String queryText, String docType, int topK) {
        if (queryText == null || queryText.isBlank() || topK <= 0) {
            return List.of();
        }
        String dt = (docType == null || docType.isBlank()) ? null : docType;
        log.info("BM25 检索 - queryText: {}, docType: {}, topK: {}", queryText, dt, topK);

        List<String> terms = hybridSearchMapper.queryTerms(queryText);
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        Bm25Rows.CorpusStats stats = corpusStats();
        if (stats == null || stats.getTotalDocs() <= 0) {
            return List.of();
        }
        List<Bm25Rows.Candidate> candidates =
                hybridSearchMapper.bm25Candidates(terms, dt, BM25_CANDIDATE_LIMIT);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Long> docFreq = new HashMap<>();
        for (Bm25Rows.TermDocFreq d : hybridSearchMapper.docFrequencies(terms)) {
            docFreq.put(d.getLexeme(), d.getDocFreq());
        }
        List<Long> ids = candidates.stream().map(Bm25Rows.Candidate::getChunkId).toList();
        Map<Long, Map<String, Integer>> tfByChunk = new HashMap<>();
        for (Bm25Rows.TermFreq t : hybridSearchMapper.termFrequencies(ids, terms)) {
            tfByChunk.computeIfAbsent(t.getChunkId(), k -> new HashMap<>()).put(t.getLexeme(), t.getTf());
        }

        List<HybridSearchResult> scored = new ArrayList<>();
        for (Bm25Rows.Candidate c : candidates) {
            double s = bm25Scorer.score(terms, tfByChunk.getOrDefault(c.getChunkId(), Map.of()),
                    c.getDocLength(), stats.getAvgDocLength(), stats.getTotalDocs(), docFreq);
            if (s > 0) {
                scored.add(HybridSearchResult.builder()
                        .chunkId(c.getChunkId())
                        .docType(c.getDocType())
                        .routeAction(c.getRouteAction())
                        .text(c.getText())
                        .rerankText(c.getRerankText())
                        .sectionPath(c.getSectionPath())
                        .score(s)
                        .build());
            }
        }
        scored.sort(Comparator.comparing(HybridSearchResult::getScore).reversed()
                .thenComparing(HybridSearchResult::getChunkId));
        List<HybridSearchResult> top = new ArrayList<>(scored.subList(0, Math.min(topK, scored.size())));
        for (int i = 0; i < top.size(); i++) {
            top.get(i).setKeywordRank(i + 1);
        }
        log.info("BM25 检索完成 - 候选 {} 条，返回 {} 条", candidates.size(), top.size());
        return top;
    }

    /** 全部 chunk 的章节路径（评估时把人工标注的 expect_section 解析成 chunk_id，不含正文）。 */
    public List<ChunkCandidate> listAllChunkSections() {
        return hybridSearchMapper.listAllChunkSections();
    }

    /** 按关键词列出候选 chunk（人工标注用）。keyword 为空返回空列表；limit 夹到 [1, 100]。 */
    public List<ChunkCandidate> searchCandidates(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return hybridSearchMapper.searchCandidates(keyword, Math.max(1, Math.min(limit, CANDIDATE_MAX_LIMIT)));
    }
}
