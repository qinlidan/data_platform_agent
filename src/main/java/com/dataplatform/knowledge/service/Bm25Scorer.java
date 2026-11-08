package com.dataplatform.knowledge.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标准 Okapi BM25 打分器（纯 Java，不依赖数据库）。
 *
 * <pre>
 * score(D, Q) = Σ_{t ∈ Q} IDF(t) * tf(t,D) * (k1 + 1) / (tf(t,D) + k1 * (1 - b + b * |D| / avgdl))
 * IDF(t)      = ln(1 + (N - n(t) + 0.5) / (n(t) + 0.5))      // 加 1 的变体，恒为正
 * </pre>
 *
 * 分词不在这里做：词项由调用方（PostgreSQL 的 jiebacfg）提供，保证与建索引时一致。
 */
public class Bm25Scorer {

    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    private final double k1;
    private final double b;

    public Bm25Scorer() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    public Bm25Scorer(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    /**
     * @param totalDocs 库内文档总数 N
     * @param docFreq   含该词的文档数 n；超出 [0, N] 时（例如统计缓存略过期）按边界截断，保证 IDF 为有限正数
     */
    public double idf(long totalDocs, long docFreq) {
        long n = Math.max(0, Math.min(docFreq, totalDocs));
        long big = Math.max(0, totalDocs);
        return Math.log(1.0 + (big - n + 0.5) / (n + 0.5));
    }

    /**
     * @param queryTerms 查询词项（重复词只计一次）
     * @param termFreqs  该文档中各词项的出现次数
     * @param docLength  该文档的词项总数 |D|
     * @param avgDocLength 库内平均文档长度 avgdl；&lt;= 0 时不做长度归一
     * @param totalDocs  N
     * @param docFreq    词项 -> n；缺失视为 0
     */
    public double score(List<String> queryTerms, Map<String, Integer> termFreqs, int docLength,
                        double avgDocLength, long totalDocs, Map<String, Long> docFreq) {
        if (queryTerms == null || queryTerms.isEmpty() || termFreqs == null || termFreqs.isEmpty()) {
            return 0.0;
        }
        double lengthNorm = avgDocLength > 0
                ? 1.0 - b + b * docLength / avgDocLength
                : 1.0;
        Set<String> seen = new HashSet<>();
        double total = 0.0;
        for (String term : queryTerms) {
            if (term == null || !seen.add(term)) {
                continue;
            }
            Integer tfObj = termFreqs.get(term);
            if (tfObj == null || tfObj <= 0) {
                continue;
            }
            double tf = tfObj;
            long n = docFreq == null ? 0L : docFreq.getOrDefault(term, 0L);
            total += idf(totalDocs, n) * tf * (k1 + 1.0) / (tf + k1 * lengthNorm);
        }
        return total;
    }
}
