package com.dataplatform.knowledge.evaluation;

import com.dataplatform.knowledge.dto.ChunkCandidate;
import com.dataplatform.knowledge.service.HybridSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 真实库验证：手工标注的每条 expect_section，在当前入库的 chunk 里都必须解析到至少一个 chunk。
 * 这是评估能不能产出有意义分数的前提——解析不到的标注会被排除在评估之外。
 * 依赖：PostgreSQL 里已经有入库并向量化好的数据。
 */
@SpringBootTest
class RealLabelResolutionIT {

    @Autowired private HybridSearchService hybridSearchService;

    @Test
    void everyHandLabeledSectionResolvesToAtLeastOneRealChunk() throws Exception {
        List<EvaluationCase> cases = new EvaluationCaseRepository(
            new ObjectMapper(), new ClassPathResource("evaluation/evaluation-cases.json")).loadCases();
        List<ChunkCandidate> corpus = hybridSearchService.listAllChunkSections();
        assertFalse(corpus.isEmpty(), "库里没有 chunk，需要先入库并完成向量化");

        SectionResolver resolver = new SectionResolver();
        List<String> report = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < cases.size(); i++) {
            EvaluationCase c = cases.get(i);
            for (String section : c.expectSections()) {
                total++;
                Set<Long> ids = resolver.resolve(section, corpus);
                report.add("第" + (i + 1) + "题\t命中" + ids.size() + "个\t" + ids + "\t" + c.getQuery());
                if (ids.isEmpty()) {
                    unresolved.add("第" + (i + 1) + "题: " + section);
                }
            }
        }
        Files.write(Path.of("target/label-resolution.tsv"), report, StandardCharsets.UTF_8);
        System.out.println("LABEL-RESOLUTION corpus=" + corpus.size() + " labels=" + total
            + " unresolved=" + unresolved.size());
        unresolved.forEach(u -> System.out.println("LABEL-UNRESOLVED " + u));
        assertEquals(0, unresolved.size(), "有标注解析不到 chunk，评估会把这些题排除: " + unresolved);
    }
}
