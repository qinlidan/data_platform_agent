package com.dataplatform.knowledge.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索指标计算（纯静态工具类）。
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /**
     * Recall@K = |top-K 去重后 ∩ groundTruth 去重后| / |groundTruth 去重后|。
     * groundTruth 或 retrieved 为空、k &lt;= 0 时返回 0；k 超过 retrieved 长度时取全部。
     */
    public static double recallAtK(List<Long> retrieved, List<Long> groundTruth, int k) {
        if (groundTruth == null || groundTruth.isEmpty() || retrieved == null || retrieved.isEmpty() || k <= 0) {
            return 0.0;
        }
        Set<Long> truth = new HashSet<>(groundTruth);
        truth.remove(null);
        if (truth.isEmpty()) {
            return 0.0;
        }
        Set<Long> topK = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        int hit = 0;
        for (Long id : truth) {
            if (topK.contains(id)) {
                hit++;
            }
        }
        return (double) hit / truth.size();
    }

    /**
     * 单个问题的倒数排名：第一个命中的名次（从 1 开始，按 retrieved 原始位置）的倒数；未命中返回 0。
     */
    public static double mrr(List<Long> retrieved, List<Long> groundTruth) {
        if (groundTruth == null || groundTruth.isEmpty() || retrieved == null || retrieved.isEmpty()) {
            return 0.0;
        }
        Set<Long> truth = new HashSet<>(groundTruth);
        for (int i = 0; i < retrieved.size(); i++) {
            Long id = retrieved.get(i);
            if (id != null && truth.contains(id)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }
}
