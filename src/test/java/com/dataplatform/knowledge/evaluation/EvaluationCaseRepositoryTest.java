package com.dataplatform.knowledge.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationCaseRepositoryTest {

    private static EvaluationCaseRepository repo(Resource r) {
        return new EvaluationCaseRepository(new ObjectMapper(), r);
    }

    private static ByteArrayResource json(String s) {
        return new ByteArrayResource(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shippedEvaluationSetParsesAndMapsFields() {
        List<EvaluationCase> cases = repo(new ClassPathResource("evaluation/evaluation-cases.json")).loadCases();

        assertThat(cases).isNotEmpty();
        EvaluationCase first = cases.get(0);
        assertThat(first.getQuery()).isNotBlank();
        assertThat(first.getCategory()).isIn("colloquial", "platform", "error_keyword");
        assertThat(first.getDocType()).isNotBlank();
        assertThat(first.getGroundTruth()).isNotNull();
    }

    @Test
    void mapsAllFieldsIncludingExpectSectionAndRefusalMarker() {
        List<EvaluationCase> cases = repo(json("""
                [{"query":"q1","category":"platform","docType":"manual","difficulty":"easy",
                  "queryType":"keyword",
                  "groundTruth":[{"expect_section":"模块A,小节B"},{"expect_section":"模块C"}]},
                 {"query":"q2","groundTruth":[{"shouldAnswer":false}]}]
                """)).loadCases();

        EvaluationCase c = cases.get(0);
        assertThat(c.getQuery()).isEqualTo("q1");
        assertThat(c.getCategory()).isEqualTo("platform");
        assertThat(c.getDocType()).isEqualTo("manual");
        assertThat(c.getDifficulty()).isEqualTo("easy");
        assertThat(c.getQueryType()).isEqualTo("keyword");
        assertThat(c.expectSections()).containsExactly("模块A,小节B", "模块C");
        assertThat(c.hasGroundTruth()).isTrue();
        assertThat(c.isRefusalCase()).isFalse();

        EvaluationCase refuse = cases.get(1);
        assertThat(refuse.isRefusalCase()).isTrue();
        assertThat(refuse.hasGroundTruth()).isFalse();
        assertThat(refuse.expectSections()).isEmpty();
    }

    @Test
    void categoryFallsBackToQueryTypeThenUnknown() {
        List<EvaluationCase> cases = repo(json("""
                [{"query":"a","queryType":"semantic","groundTruth":[{"expect_section":"x"}]},
                 {"query":"b","groundTruth":[{"expect_section":"x"}]},
                 {"query":"c","category":"  ","queryType":"hybrid","groundTruth":[{"expect_section":"x"}]},
                 {"query":"d","category":"error_keyword","queryType":"hybrid","groundTruth":[{"expect_section":"x"}]}]
                """)).loadCases();

        // 加载后 category 已规范化填充，GET /cases 直接可用
        assertThat(cases).extracting(EvaluationCase::getCategory)
                .containsExactly("semantic", "unknown", "hybrid", "error_keyword");
        // 旧字段原样保留
        assertThat(cases.get(0).getQueryType()).isEqualTo("semantic");
        assertThat(cases.get(1).getQueryType()).isNull();
    }

    @Test
    void resolvedCategoryWorksOnProgrammaticallyBuiltCases() {
        assertThat(EvaluationCase.builder().queryType("keyword").build().resolvedCategory()).isEqualTo("keyword");
        assertThat(EvaluationCase.builder().build().resolvedCategory()).isEqualTo("unknown");
        assertThat(EvaluationCase.builder().category("platform").queryType("x").build().resolvedCategory())
                .isEqualTo("platform");
    }

    @Test
    void hasGroundTruthIgnoresNullEmptyBlankAndRefusalOnly() {
        assertThat(EvaluationCase.builder().build().hasGroundTruth()).isFalse();
        assertThat(EvaluationCase.builder().groundTruth(List.of()).build().hasGroundTruth()).isFalse();
        assertThat(EvaluationCase.builder().groundTruth(Arrays.asList((GroundTruthItem) null)).build().hasGroundTruth())
                .isFalse();
        assertThat(EvaluationCase.builder().groundTruth(List.of(GroundTruthItem.section("  "))).build().hasGroundTruth())
                .isFalse();
        assertThat(EvaluationCase.builder().groundTruth(List.of(GroundTruthItem.refusal())).build().hasGroundTruth())
                .isFalse();
        assertThat(EvaluationCase.builder().groundTruth(List.of(GroundTruthItem.section("a"))).build().hasGroundTruth())
                .isTrue();
    }

    @Test
    void unknownFieldsAreIgnored() {
        assertThat(repo(json("[{\"query\":\"q\",\"extra\":1,\"groundTruth\":[{\"expect_section\":\"a\",\"extra\":2}]}]"))
                .loadCases()).hasSize(1);
    }

    @Test
    void helperMethodsAreNotSerializedAsJsonProperties() throws Exception {
        String out = new ObjectMapper().writeValueAsString(EvaluationCase.builder().query("q").build());
        assertThat(out).doesNotContain("resolvedCategory").doesNotContain("hasGroundTruth");
    }

    @Test
    void missingFileThrowsClearError(@TempDir Path dir) {
        var r = repo(new FileSystemResource(dir.resolve("nope.json")));
        assertThatThrownBy(r::loadCases)
                .isInstanceOf(EvaluationCaseLoadException.class)
                .hasMessageContaining("nope.json");
    }

    @Test
    void invalidJsonThrowsClearError(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.json");
        Files.writeString(f, "[{\"query\": ");
        assertThatThrownBy(() -> repo(new FileSystemResource(f)).loadCases())
                .isInstanceOf(EvaluationCaseLoadException.class)
                .hasMessageContaining("bad.json");
    }

    @Test
    void nonArrayJsonThrows() {
        assertThatThrownBy(() -> repo(json("{\"query\":\"q\"}")).loadCases())
                .isInstanceOf(EvaluationCaseLoadException.class);
    }

    @Test
    void blankQueryInFileThrows() {
        assertThatThrownBy(() -> repo(json("[{\"query\":\"  \",\"groundTruth\":[{\"expect_section\":\"a\"}]}]")).loadCases())
                .isInstanceOf(EvaluationCaseLoadException.class)
                .hasMessageContaining("query");
    }
}
