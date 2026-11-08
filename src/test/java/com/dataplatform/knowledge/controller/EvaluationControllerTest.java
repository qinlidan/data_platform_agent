package com.dataplatform.knowledge.controller;

import com.dataplatform.knowledge.dto.ChunkCandidate;
import com.dataplatform.knowledge.evaluation.EvaluationCase;
import com.dataplatform.knowledge.evaluation.EvaluationCaseLoadException;
import com.dataplatform.knowledge.evaluation.EvaluationCaseRepository;
import com.dataplatform.knowledge.evaluation.EvaluationResultDTO;
import com.dataplatform.knowledge.evaluation.EvaluationService;
import com.dataplatform.knowledge.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** standaloneSetup：绕开 Spring Security 与其它并行改动，只验证控制器本身。 */
class EvaluationControllerTest {

    private EvaluationService evaluationService;
    private EvaluationCaseRepository repository;
    private HybridSearchService searchService;
    private EvaluationController controller;
    private MockMvc mvc;

    private static EvaluationCase c(String q) {
        return EvaluationCase.builder().query(q).category("platform").groundTruth(List.of(com.dataplatform.knowledge.evaluation.GroundTruthItem.section("模块1,小节1"))).build();
    }

    private static String body(int n) {
        StringBuilder sb = new StringBuilder("{\"cases\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"query\":\"q").append(i).append("\",\"category\":\"platform\",\"groundTruth\":[{\"expect_section\":\"模块1\"}]}");
        }
        return sb.append("]}").toString();
    }

    @BeforeEach
    void setUp() {
        evaluationService = Mockito.mock(EvaluationService.class);
        repository = Mockito.mock(EvaluationCaseRepository.class);
        searchService = Mockito.mock(HybridSearchService.class);
        controller = new EvaluationController(evaluationService, repository, searchService, 3);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(evaluationService.evaluate(anyList())).thenReturn(EvaluationResultDTO.builder().recallK(5).totalCases(1).build());
    }

    @Test
    void getCasesReturnsStoredSetInApiResponseEnvelope() throws Exception {
        when(repository.loadCases()).thenReturn(List.of(c("a"), c("b")));
        mvc.perform(get("/api/knowledge/evaluation/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].query").value("a"))
                .andExpect(jsonPath("$.data[0].category").value("platform"));
    }

    @Test
    void getCasesReportsLoadFailureExplicitly() throws Exception {
        when(repository.loadCases()).thenThrow(new EvaluationCaseLoadException("评估集加载失败: 文件缺失"));
        mvc.perform(get("/api/knowledge/evaluation/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("评估集加载失败")));
    }

    @Test
    void runWithoutBodyUsesStoredSet() throws Exception {
        when(repository.loadCases()).thenReturn(List.of(c("a")));
        mvc.perform(post("/api/knowledge/evaluation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recallK").value(5));
        verify(evaluationService).evaluate(List.of(c("a")));
    }

    @Test
    void runWithEmptyObjectBodyUsesStoredSet() throws Exception {
        when(repository.loadCases()).thenReturn(List.of(c("a")));
        mvc.perform(post("/api/knowledge/evaluation/run").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(evaluationService).evaluate(List.of(c("a")));
    }

    @Test
    void runWithBodyUsesSubmittedCases() throws Exception {
        mvc.perform(post("/api/knowledge/evaluation/run").contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(jsonPath("$.code").value(200));
        verify(repository, never()).loadCases();
        verify(evaluationService).evaluate(Mockito.argThat(l -> l.size() == 2 && "q0".equals(l.get(0).getQuery())));
    }

    @Test
    void runRejectsOverLimitInsteadOfTruncating() throws Exception {
        mvc.perform(post("/api/knowledge/evaluation/run").contentType(MediaType.APPLICATION_JSON).content(body(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("4"), org.hamcrest.Matchers.containsString("3"))));
        verify(evaluationService, never()).evaluate(anyList());
    }

    @Test
    void runAtExactlyTheLimitIsAllowed() throws Exception {
        mvc.perform(post("/api/knowledge/evaluation/run").contentType(MediaType.APPLICATION_JSON).content(body(3)))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void storedSetOverLimitIsAlsoRejected() throws Exception {
        List<EvaluationCase> big = new ArrayList<>();
        for (int i = 0; i < 4; i++) big.add(c("q" + i));
        when(repository.loadCases()).thenReturn(big);
        mvc.perform(post("/api/knowledge/evaluation/run"))
                .andExpect(jsonPath("$.code").value(400));
        verify(evaluationService, never()).evaluate(anyList());
    }

    @Test
    void runRejectsExplicitEmptyListAndBlankQuery() throws Exception {
        mvc.perform(post("/api/knowledge/evaluation/run").contentType(MediaType.APPLICATION_JSON).content("{\"cases\":[]}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/knowledge/evaluation/run").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cases\":[{\"query\":\"  \",\"groundTruth\":[{\"expect_section\":\"模块1\"}]}]}"))
                .andExpect(jsonPath("$.code").value(400));
        verify(evaluationService, never()).evaluate(anyList());
    }

    @Test
    void runReportsStoredSetLoadFailure() throws Exception {
        when(repository.loadCases()).thenThrow(new EvaluationCaseLoadException("JSON 非法"));
        mvc.perform(post("/api/knowledge/evaluation/run"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("JSON 非法")));
    }

    @Test
    void concurrentRunIsRejectedImmediatelyAndLockReleasedAfterwards() throws Exception {
        when(repository.loadCases()).thenReturn(List.of(c("a")));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(evaluationService.evaluate(anyList())).thenAnswer(inv -> {
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return EvaluationResultDTO.builder().recallK(5).build();
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> {
                try {
                    mvc.perform(post("/api/knowledge/evaluation/run"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

            // 第一个还在跑：第二个立刻被拒，且不会触发第二次评估
            mvc.perform(post("/api/knowledge/evaluation/run"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("正在运行")));
            verify(evaluationService, Mockito.times(1)).evaluate(anyList());

            release.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        // 跑完后锁已释放，可以再次运行
        Mockito.doReturn(EvaluationResultDTO.builder().recallK(5).build()).when(evaluationService).evaluate(anyList());
        mvc.perform(post("/api/knowledge/evaluation/run")).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void lockIsReleasedWhenEvaluationThrows() throws Exception {
        when(repository.loadCases()).thenReturn(List.of(c("a")));
        when(evaluationService.evaluate(anyList())).thenThrow(new IllegalStateException("boom"));
        mvc.perform(post("/api/knowledge/evaluation/run")).andExpect(jsonPath("$.code").value(500));

        Mockito.doReturn(EvaluationResultDTO.builder().recallK(5).build()).when(evaluationService).evaluate(anyList());
        mvc.perform(post("/api/knowledge/evaluation/run")).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void candidatesDelegatesWithDefaultLimit() throws Exception {
        ChunkCandidate cc = new ChunkCandidate();
        cc.setChunkId(5L);
        cc.setDocType("manual");
        cc.setSectionPath("A / B");
        cc.setText("abc");
        when(searchService.searchCandidates("内存", 20)).thenReturn(List.of(cc));

        mvc.perform(get("/api/knowledge/evaluation/candidates").param("keyword", "内存"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].chunkId").value(5))
                .andExpect(jsonPath("$.data[0].docType").value("manual"))
                .andExpect(jsonPath("$.data[0].sectionPath").value("A / B"))
                .andExpect(jsonPath("$.data[0].text").value("abc"));
    }

    @Test
    void candidatesPassesExplicitLimitAndEmptyKeywordReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/knowledge/evaluation/candidates").param("keyword", "x").param("limit", "5"))
                .andExpect(jsonPath("$.code").value(200));
        verify(searchService).searchCandidates("x", 5);

        mvc.perform(get("/api/knowledge/evaluation/candidates"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/knowledge/evaluation/candidates").param("keyword", "   "))
                .andExpect(jsonPath("$.data.length()").value(0));
        verify(searchService, never()).searchCandidates("   ", 20);
    }
}
