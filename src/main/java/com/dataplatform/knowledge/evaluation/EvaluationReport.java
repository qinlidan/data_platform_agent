package com.dataplatform.knowledge.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 评估报告：策略 × 分桶(queryType) × 指标。
 *
 * 平均值只对成功的 case 计算；失败的 case 仅计入 failedCount，不当作 0 分。
 */
public class EvaluationReport {

    public enum Strategy {
        VECTOR("Vector"),
        KEYWORD("Keyword"),
        HYBRID("Hybrid"),
        HYBRID_RERANK("Hybrid+Rerank");

        private final String label;

        Strategy(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 某个策略在某个分桶（或总体）上的汇总。 */
    public static class StrategyStats {
        private int successCount;
        private int failedCount;
        private double sumRecall;
        private double sumMrr;

        void addSuccess(double recall, double mrr) {
            successCount++;
            sumRecall += recall;
            sumMrr += mrr;
        }

        void addFailure() {
            failedCount++;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        /** 是否有可计算平均值的成功样本 */
        public boolean hasData() {
            return successCount > 0;
        }

        /** 成功 case 的 Recall@K 平均；无成功样本时为 NaN（请先用 hasData 判断） */
        public double getAvgRecallAtK() {
            return successCount == 0 ? Double.NaN : sumRecall / successCount;
        }

        /** 成功 case 的 MRR 平均；无成功样本时为 NaN */
        public double getAvgMrr() {
            return successCount == 0 ? Double.NaN : sumMrr / successCount;
        }
    }

    /** 一次失败记录，便于排查。 */
    public static class Failure {
        private final String query;
        private final Strategy strategy;
        private final String message;

        Failure(String query, Strategy strategy, String message) {
            this.query = query;
            this.strategy = strategy;
            this.message = message;
        }

        public String getQuery() {
            return query;
        }

        public Strategy getStrategy() {
            return strategy;
        }

        public String getMessage() {
            return message;
        }
    }

    private final int recallK;
    private final Map<String, Map<Strategy, StrategyStats>> buckets = new LinkedHashMap<>();
    private final Map<Strategy, StrategyStats> overall = newStatsMap();
    private final List<Failure> failures = new ArrayList<>();

    public EvaluationReport(int recallK) {
        this.recallK = recallK;
    }

    private static Map<Strategy, StrategyStats> newStatsMap() {
        Map<Strategy, StrategyStats> m = new EnumMap<>(Strategy.class);
        for (Strategy s : Strategy.values()) {
            m.put(s, new StrategyStats());
        }
        return m;
    }

    private Map<Strategy, StrategyStats> bucket(String name) {
        return buckets.computeIfAbsent(name, k -> newStatsMap());
    }

    void recordSuccess(String bucket, Strategy strategy, double recall, double mrr) {
        bucket(bucket).get(strategy).addSuccess(recall, mrr);
        overall.get(strategy).addSuccess(recall, mrr);
    }

    void recordFailure(String bucket, Strategy strategy, String query, String message) {
        bucket(bucket).get(strategy).addFailure();
        overall.get(strategy).addFailure();
        failures.add(new Failure(query, strategy, message));
    }

    public int getRecallK() {
        return recallK;
    }

    public List<String> getBucketNames() {
        return new ArrayList<>(buckets.keySet());
    }

    public StrategyStats getOverall(Strategy strategy) {
        return overall.get(strategy);
    }

    /** 取某分桶下某策略的汇总；分桶不存在时返回空汇总。 */
    public StrategyStats getStats(String bucket, Strategy strategy) {
        Map<Strategy, StrategyStats> m = buckets.get(bucket);
        return m == null ? new StrategyStats() : m.get(strategy);
    }

    public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    /** 人读的对比表。 */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<Strategy, StrategyStats>> e : buckets.entrySet()) {
            appendSection(sb, "Query Type: " + e.getKey(), e.getValue());
        }
        appendSection(sb, "Overall", overall);
        if (!failures.isEmpty()) {
            sb.append("Failures:\n");
            for (Failure f : failures) {
                sb.append("  [").append(f.strategy.label).append("] ")
                        .append(f.query).append(" -> ").append(f.message).append('\n');
            }
        }
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, Map<Strategy, StrategyStats> stats) {
        sb.append(title).append('\n');
        for (Strategy s : Strategy.values()) {
            StrategyStats st = stats.get(s);
            sb.append("  ").append(s.label).append(": ");
            if (st.hasData()) {
                sb.append(String.format(Locale.ROOT, "R@%d=%.2f, MRR=%.2f", recallK,
                        st.getAvgRecallAtK(), st.getAvgMrr()));
            } else {
                sb.append("R@").append(recallK).append("=N/A, MRR=N/A");
            }
            if (st.getFailedCount() > 0) {
                sb.append(" (").append(st.getFailedCount()).append(" 个失败已排除)");
            }
            sb.append('\n');
        }
    }
}
