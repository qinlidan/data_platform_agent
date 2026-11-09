package com.dataplatform.knowledge.confidence;

import com.dataplatform.knowledge.service.HybridSearchService;
import com.dataplatform.knowledge.service.RerankService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 置信度闸阈值标定服务
 * 基于正例+反例评估集计算最优阈值并生成完整标定报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdCalibrator {

    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final ConfidenceGate confidenceGate;
    private final ObjectMapper objectMapper;

    private static final int HYBRID_TOP_K = 30;
    private static final int RERANK_TOP_N = 5;

    /**
     * 完整标定报告
     */
    @Data
    public static class CalibrationReport {
        // 推荐阈值
        private double recommendedThreshold;

        // 该阈值下的性能指标
        private double accuracy;          // 可答通过率
        private double recall;            // 可答召回率
        private double rejectionRate;     // 总拒答率
        private double youdenIndex;       // Youden指数

        // 样本统计
        private int totalSamples;
        private int answerableSamples;    // 可答样本数
        private int refusalSamples;       // 应拒答样本数

        // 阈值扫描曲线数据（用于前端绘图）
        private List<ThresholdPoint> curve;

        // 置信度分布统计（按类别分桶）
        private DistributionStats distribution;

        // 多策略推荐
        private Map<String, Double> recommendations;
    }

    /**
     * 阈值扫描曲线上的点
     */
    @Data
    @AllArgsConstructor
    public static class ThresholdPoint {
        private double threshold;
        private double answerablePassRate;  // 可答通过率
        private double refusalRejectRate;   // 应拒答拒绝率
        private double combinedAccuracy;    // 综合准确率
        private double recallSide;          // 召回侧值（可答召回率）
    }

    /**
     * 置信度分布统计
     */
    @Data
    public static class DistributionStats {
        private CategoryBucket colloquial;     // 口语化
        private CategoryBucket errorKeyword;   // 关键词
        private CategoryBucket platform;       // 平台问题
        private CategoryBucket answerable;     // 应答
    }

    /**
     * 单个类别的分布桶
     */
    @Data
    @AllArgsConstructor
    public static class CategoryBucket {
        private int count;
        private double min;
        private double p25;
        private double median;
        private double p75;
        private double max;
    }

    /**
     * 评估样本
     */
    @Data
    @AllArgsConstructor
    private static class EvaluationSample {
        private String query;
        private String category;  // colloquial, error_keyword, platform
        private boolean shouldAnswer;
        private double confidenceScore;
    }

    /**
     * 执行完整标定
     */
    public CalibrationReport calibrateFromEvaluation() throws IOException {
        // 加载正例+反例
        List<EvaluationSample> answerableSamples = loadAnswerableCases();
        List<EvaluationSample> refusalSamples = loadRefusalCases();

        log.info("Loaded {} answerable cases, {} refusal cases",
                answerableSamples.size(), refusalSamples.size());

        // 计算每个样本的置信度分数
        computeConfidenceScores(answerableSamples);
        computeConfidenceScores(refusalSamples);

        // 合并
        List<EvaluationSample> allSamples = new ArrayList<>();
        allSamples.addAll(answerableSamples);
        allSamples.addAll(refusalSamples);

        // 生成完整报告
        CalibrationReport report = new CalibrationReport();
        report.setTotalSamples(allSamples.size());
        report.setAnswerableSamples(answerableSamples.size());
        report.setRefusalSamples(refusalSamples.size());

        // 扫描阈值空间，生成曲线
        report.setCurve(scanThresholdSpace(allSamples, answerableSamples.size()));

        // 找最优阈值点
        ThresholdPoint optimal = report.getCurve().stream()
                .max(Comparator.comparing(p -> p.getCombinedAccuracy() + p.getRecallSide() - 1.0))
                .orElseThrow();

        report.setRecommendedThreshold(optimal.getThreshold());
        report.setAccuracy(optimal.getAnswerablePassRate());
        report.setRecall(optimal.getRecallSide());
        report.setRejectionRate(optimal.getRefusalRejectRate());  // 直接使用应拒拒绝率
        report.setYoudenIndex(optimal.getCombinedAccuracy() + optimal.getRecallSide() - 1.0);

        // 计算分布统计
        report.setDistribution(computeDistribution(allSamples));

        // 多策略推荐
        report.setRecommendations(computeRecommendations(report.getCurve()));

        log.info("Calibration completed: recommended_threshold={}, youden_index={}",
                report.getRecommendedThreshold(), report.getYoudenIndex());

        return report;
    }

    /**
     * 计算置信度分数（批量调用检索+重排）
     */
    private void computeConfidenceScores(List<EvaluationSample> samples) {
        for (EvaluationSample sample : samples) {
            try {
                var candidates = hybridSearchService.hybridSearch(sample.query, null, HYBRID_TOP_K);
                if (candidates == null || candidates.isEmpty()) {
                    sample.setConfidenceScore(0.0);
                    continue;
                }

                var reranked = rerankService.rerank(sample.query, candidates, RERANK_TOP_N);
                var confidence = confidenceGate.evaluate(reranked);
                sample.setConfidenceScore(confidence.getOverallScore());

            } catch (Exception e) {
                log.warn("Failed to compute confidence for query: {}", sample.query, e);
                sample.setConfidenceScore(0.0);
            }
        }
    }

    /**
     * 扫描阈值空间 [0.05, 0.95]，步长0.01
     */
    private List<ThresholdPoint> scanThresholdSpace(
            List<EvaluationSample> allSamples, int answerableCount) {

        List<ThresholdPoint> points = new ArrayList<>();
        int refusalCount = allSamples.size() - answerableCount;

        for (double t = 0.05; t <= 0.95; t += 0.01) {
            final double threshold = t;

            // 可答样本通过数
            long answerablePassed = allSamples.stream()
                    .filter(s -> s.shouldAnswer && s.confidenceScore >= threshold)
                    .count();

            // 应拒答样本拒绝数
            long refusalRejected = allSamples.stream()
                    .filter(s -> !s.shouldAnswer && s.confidenceScore < threshold)
                    .count();

            // 总通过数
            long totalPassed = allSamples.stream()
                    .filter(s -> s.confidenceScore >= threshold)
                    .count();

            // 避免除0
            double answerablePassRate = totalPassed > 0 ? (double) answerablePassed / totalPassed : 0.0;
            double refusalRejectRate = refusalCount > 0 ? (double) refusalRejected / refusalCount : 0.0;
            double combinedAccuracy = allSamples.size() > 0
                    ? (double) (answerablePassed + refusalRejected) / allSamples.size()
                    : 0.0;
            double recallSide = answerableCount > 0 ? (double) answerablePassed / answerableCount : 0.0;

            points.add(new ThresholdPoint(
                    Math.round(threshold * 100) / 100.0,
                    answerablePassRate,
                    refusalRejectRate,
                    combinedAccuracy,
                    recallSide
            ));
        }

        return points;
    }

    /**
     * 计算分布统计（按类别分桶）
     */
    private DistributionStats computeDistribution(List<EvaluationSample> samples) {
        Map<String, List<Double>> byCategory = samples.stream()
                .collect(Collectors.groupingBy(
                        s -> s.shouldAnswer ? "answerable" : s.category,
                        Collectors.mapping(s -> s.confidenceScore, Collectors.toList())
                ));

        DistributionStats stats = new DistributionStats();
        stats.setColloquial(computeBucket(byCategory.getOrDefault("colloquial", List.of())));
        stats.setErrorKeyword(computeBucket(byCategory.getOrDefault("error_keyword", List.of())));
        stats.setPlatform(computeBucket(byCategory.getOrDefault("platform", List.of())));
        stats.setAnswerable(computeBucket(byCategory.getOrDefault("answerable", List.of())));

        return stats;
    }

    /**
     * 计算单个桶的统计量
     */
    private CategoryBucket computeBucket(List<Double> scores) {
        if (scores.isEmpty()) {
            return new CategoryBucket(0, 0, 0, 0, 0, 0);
        }

        List<Double> sorted = new ArrayList<>(scores);
        Collections.sort(sorted);

        int count = sorted.size();
        double min = sorted.get(0);
        double max = sorted.get(count - 1);
        double p25 = percentile(sorted, 0.25);
        double median = percentile(sorted, 0.50);
        double p75 = percentile(sorted, 0.75);

        return new CategoryBucket(count, min, p25, median, p75, max);
    }

    private double percentile(List<Double> sorted, double p) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    /**
     * 多策略推荐
     */
    private Map<String, Double> computeRecommendations(List<ThresholdPoint> curve) {
        Map<String, Double> rec = new HashMap<>();

        // Youden指数最大
        ThresholdPoint youden = curve.stream()
                .max(Comparator.comparing(p -> p.getCombinedAccuracy() + p.getRecallSide() - 1.0))
                .orElse(null);
        if (youden != null) {
            rec.put("youden_max", youden.getThreshold());
        }

        // 最高召回率（允许accuracy>=0.8）
        ThresholdPoint highRecall = curve.stream()
                .filter(p -> p.getCombinedAccuracy() >= 0.8)
                .max(Comparator.comparing(ThresholdPoint::getRecallSide))
                .orElse(null);
        if (highRecall != null) {
            rec.put("high_recall", highRecall.getThreshold());
        }

        // 最高准确率（允许recall>=0.7）
        ThresholdPoint highAccuracy = curve.stream()
                .filter(p -> p.getRecallSide() >= 0.7)
                .max(Comparator.comparing(ThresholdPoint::getCombinedAccuracy))
                .orElse(null);
        if (highAccuracy != null) {
            rec.put("high_accuracy", highAccuracy.getThreshold());
        }

        return rec;
    }

    /**
     * 加载正例（可答问题）
     */
    private List<EvaluationSample> loadAnswerableCases() throws IOException {
        ClassPathResource resource = new ClassPathResource("evaluation/evaluation-cases.json");
        if (!resource.exists()) {
            throw new IllegalStateException("Answerable cases file not found");
        }

        JsonNode root = objectMapper.readTree(resource.getInputStream());
        List<EvaluationSample> samples = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode node : root) {
                String query = node.has("query") ? node.get("query").asText() : null;
                if (query == null || query.isBlank()) continue;

                String category = node.has("category") ? node.get("category").asText() : "unknown";

                // 正例：有groundTruth且非空
                boolean hasGroundTruth = node.has("groundTruth")
                        && node.get("groundTruth").isArray()
                        && node.get("groundTruth").size() > 0;

                if (hasGroundTruth) {
                    samples.add(new EvaluationSample(query, category, true, 0.0));
                }
            }
        }

        return samples;
    }

    /**
     * 加载反例（应拒答问题）
     */
    private List<EvaluationSample> loadRefusalCases() throws IOException {
        ClassPathResource resource = new ClassPathResource("evaluation/refusal-cases-draft.json");
        if (!resource.exists()) {
            log.warn("Refusal cases file not found, using empty list");
            return List.of();
        }

        JsonNode root = objectMapper.readTree(resource.getInputStream());
        List<EvaluationSample> samples = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode node : root) {
                String query = node.has("query") ? node.get("query").asText() : null;
                if (query == null || query.isBlank()) continue;

                String category = node.has("category") ? node.get("category").asText() : "unknown";
                boolean shouldAnswer = node.has("shouldAnswer") && node.get("shouldAnswer").asBoolean();

                // 反例：shouldAnswer = false
                if (!shouldAnswer) {
                    samples.add(new EvaluationSample(query, category, false, 0.0));
                }
            }
        }

        return samples;
    }
}
