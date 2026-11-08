package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端真实评估：真实 PostgreSQL（BM25 / 向量 / 混合）+ 真实重排接口，跑完整的人工标注集。
 * 会调用收费的重排接口（约 35 次），所以只在显式指定 -Dtest=RealEvaluationRunIT 时运行。
 * 结果写到 target/evaluation-result.json 供查看，不在测试里断言具体分数（分数是被测对象，不是预期值）。
 */
@SpringBootTest
class RealEvaluationRunIT {

    @Autowired private EvaluationService evaluationService;
    @Autowired private EvaluationCaseRepository repository;

    @Test
    void runsTheFullHandLabeledSetOnRealBackends() throws Exception {
        List<EvaluationCase> cases = repository.loadCases();
        EvaluationResultDTO result = evaluationService.evaluate(cases);

        new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(Path.of("target/evaluation-result.json").toFile(), result);

        System.out.println("EVAL total=" + result.getTotalCases() + " skipped=" + result.getSkippedCases()
            + " refusal=" + result.getRefusalCases() + " unresolved=" + result.getUnresolvedCases().size()
            + " failures=" + result.getFailures().size());
        for (Map.Entry<String, Map<String, EvaluationResultDTO.StrategyMetrics>> b : result.getSummary().entrySet()) {
            for (Map.Entry<String, EvaluationResultDTO.StrategyMetrics> s : b.getValue().entrySet()) {
                EvaluationResultDTO.StrategyMetrics m = s.getValue();
                System.out.println(String.format("EVAL %-12s %-14s n=%-2d fail=%d R@5=%s MRR=%s",
                    b.getKey(), s.getKey(), m.getSampleCount(), m.getFailedCount(),
                    m.getRecallAtK() == null ? "N/A" : String.format("%.3f", m.getRecallAtK()),
                    m.getMrr() == null ? "N/A" : String.format("%.3f", m.getMrr())));
            }
        }

        // 只断言"管线跑通且数据完整"，不断言分数高低
        assertEquals(35, result.getTotalCases(), "35 道有章节标注的召回题都应参与评估");
        assertEquals(4, result.getRefusalCases(), "4 道应拒答题不参与召回评估");
        assertEquals(1, result.getSkippedCases(), "1 道未标注的题被跳过");
        assertTrue(result.getUnresolvedCases().isEmpty(), "不应有解析不到 chunk 的标注");
        assertNotNull(result.getSummary().get("ALL"));
    }
}
