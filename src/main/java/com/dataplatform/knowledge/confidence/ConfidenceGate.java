package com.dataplatform.knowledge.confidence;

import com.dataplatform.knowledge.dto.RerankResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 置信度闸计算器
 * 基于检索和精排结果计算综合置信度分数，判断是否通过闸门
 */
@Slf4j
@Component
public class ConfidenceGate {

    // 信号权重
    private static final double WEIGHT_TOP_SCORE = 0.6;      // Top1分数权重
    private static final double WEIGHT_COUNT = 0.2;          // 证据数量权重
    private static final double WEIGHT_DELTA = 0.2;          // 分差权重

    // 归一化参数
    private static final double MAX_RERANK_SCORE = 1.0;      // 精排分数最大值
    private static final int IDEAL_EVIDENCE_COUNT = 5;       // 理想证据数量
    private static final double EXPECTED_DELTA = 0.2;        // 期望的分差

    private final double threshold;

    public ConfidenceGate(
            @Value("${knowledge.confidence.threshold:0.17}") double threshold) {
        this.threshold = threshold;
        log.info("ConfidenceGate initialized with threshold: {}", threshold);
    }

    /**
     * 计算置信度并判断是否通过闸门
     *
     * @param rerankResults 精排结果列表（按relevanceScore降序）
     * @return 置信度评估结果
     */
    public EvidenceConfidence evaluate(List<RerankResult> rerankResults) {
        if (rerankResults == null || rerankResults.isEmpty()) {
            return buildRejection(null, 0, null, "无精排结果");
        }

        // 提取信号
        Double topScore = rerankResults.get(0).getRelevanceScore();
        int count = rerankResults.size();
        Double scoreDelta = null;
        if (rerankResults.size() >= 2) {
            double top2Score = rerankResults.get(1).getRelevanceScore();
            scoreDelta = topScore - top2Score;
        }

        // 归一化各信号到 [0, 1]
        double normalizedTopScore = normalizeTopScore(topScore);
        double normalizedCount = normalizeCount(count);
        double normalizedDelta = normalizeDelta(scoreDelta);

        // 加权计算综合分数
        double overallScore = WEIGHT_TOP_SCORE * normalizedTopScore
                            + WEIGHT_COUNT * normalizedCount
                            + WEIGHT_DELTA * normalizedDelta;

        // 判断是否通过
        boolean passed = overallScore >= threshold;

        EvidenceConfidence.EvidenceConfidenceBuilder builder = EvidenceConfidence.builder()
                .topRelevanceScore(topScore)
                .validEvidenceCount(count)
                .scoreDelta(scoreDelta)
                .overallScore(overallScore)
                .passed(passed);

        if (!passed) {
            String reason = buildRejectionReason(overallScore, normalizedTopScore,
                    normalizedCount, normalizedDelta);
            builder.rejectionReason(reason);
        }

        return builder.build();
    }

    /**
     * 归一化Top1分数：简单线性映射
     */
    private double normalizeTopScore(Double score) {
        if (score == null || score < 0) {
            return 0.0;
        }
        return Math.min(score / MAX_RERANK_SCORE, 1.0);
    }

    /**
     * 归一化证据数量：达到理想数量得1分，超过不额外加分
     */
    private double normalizeCount(int count) {
        return Math.min((double) count / IDEAL_EVIDENCE_COUNT, 1.0);
    }

    /**
     * 归一化分差：达到期望分差得1分，超过不额外加分
     */
    private double normalizeDelta(Double delta) {
        if (delta == null || delta < 0) {
            return 0.0;
        }
        return Math.min(delta / EXPECTED_DELTA, 1.0);
    }

    /**
     * 构建拒绝原因
     */
    private String buildRejectionReason(double overall, double normTop, double normCount, double normDelta) {
        return String.format("综合置信度%.3f < 阈值%.2f (Top1分:%.3f, 证据数:%.3f, 分差:%.3f)",
                overall, threshold, normTop, normCount, normDelta);
    }

    private EvidenceConfidence buildRejection(Double topScore, int count, Double delta, String reason) {
        return EvidenceConfidence.builder()
                .topRelevanceScore(topScore)
                .validEvidenceCount(count)
                .scoreDelta(delta)
                .overallScore(0.0)
                .passed(false)
                .rejectionReason(reason)
                .build();
    }
}
