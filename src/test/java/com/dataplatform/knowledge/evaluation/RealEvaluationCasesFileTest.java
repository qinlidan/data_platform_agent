package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对"真实的"手工标注评估集（src/main/resources/evaluation/evaluation-cases.json）的回归测试。
 * 之前的测试都用自造的小样例，文件格式一变，只有跑评估时才会发现解析失败。
 * 注意：标注文字是人工写的，这里只校验"能被读进来、结构合法"，不改它的内容。
 */
class RealEvaluationCasesFileTest {

    private List<EvaluationCase> load() {
        EvaluationCaseRepository repo = new EvaluationCaseRepository(
            new ObjectMapper(), new ClassPathResource("evaluation/evaluation-cases.json"));
        return repo.loadCases();
    }

    @Test
    void realFileLoadsWithoutError() {
        List<EvaluationCase> cases = load();
        assertEquals(40, cases.size());
        for (EvaluationCase c : cases) {
            assertFalse(c.getQuery().isBlank());
        }
    }

    @Test
    void everyCaseHasAKnownCategory() {
        for (EvaluationCase c : load()) {
            assertTrue(List.of("colloquial", "platform", "error_keyword").contains(c.resolvedCategory()),
                "未知类别: " + c.resolvedCategory() + " @ " + c.getQuery());
        }
    }

    @Test
    void expectSectionIsReadAsStringLabels() {
        EvaluationCase first = load().get(0);
        assertTrue(first.hasGroundTruth());
        assertTrue(first.expectSections().get(0).contains("Java heap space"));
    }

    @Test
    void everyCaseIsEitherLabeledOrMarkedRefusalOrExplicitlyUnlabeled() {
        // 40 题里：召回题（有 expect_section）+ 拒答题（shouldAnswer=false）+ 尚未标注的题，三者必居其一且互斥
        int labeled = 0, refusal = 0, unlabeled = 0;
        for (EvaluationCase c : load()) {
            if (c.hasGroundTruth()) labeled++;
            else if (c.isRefusalCase()) refusal++;
            else unlabeled++;
        }
        assertEquals(40, labeled + refusal + unlabeled);
        assertTrue(labeled >= 30, "已标注的召回题应不少于 30 题，实际 " + labeled);
        System.out.println("REAL-CASES labeled=" + labeled + " refusal=" + refusal + " unlabeled=" + unlabeled);
    }
}
