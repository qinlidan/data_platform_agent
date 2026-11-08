package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.RerankService.RerankException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** RerankService 单元测试：请求格式、结果映射、越界保护、失败不静默。 */
class RerankServiceTest {

    private static final String URL = "http://rerank.test/api";

    private final RestTemplate rt = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(rt);
    private final RerankService service = new RerankService(rt, "sk-test", URL, "qwen3.7-text-rerank");

    private static HybridSearchResult cand(long id, String rerankText, String text, double sim) {
        return HybridSearchResult.builder().chunkId(id).rerankText(rerankText).text(text)
            .sectionPath("路径" + id).vectorSimilarity(sim).build();
    }

    private static final List<HybridSearchResult> CANDIDATES = List.of(
        cand(10, "性能优化 调整并行度", "doc 性能优化", 0.7),
        cand(20, "内存溢出 增加executor内存", "doc 内存溢出", 0.8),
        cand(30, null, "仅有text的候选", 0.5));

    @Test
    void sendsDocumentedRequestShapeAndMapsIndexBackToChunks() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer sk-test"))
            .andExpect(jsonPath("$.model").value("qwen3.7-text-rerank"))
            .andExpect(jsonPath("$.input.query").value("Spark 内存溢出"))
            .andExpect(jsonPath("$.input.documents[0]").value("性能优化 调整并行度"))
            .andExpect(jsonPath("$.input.documents[2]").value("仅有text的候选"))
            .andExpect(jsonPath("$.parameters.top_n").value(2))
            .andExpect(jsonPath("$.parameters.return_documents").value(false))
            .andRespond(withSuccess("""
                {"output":{"results":[{"index":1,"relevance_score":0.95},{"index":0,"relevance_score":0.15}]}}
                """, MediaType.APPLICATION_JSON));

        List<RerankResult> out = service.rerank("Spark 内存溢出", CANDIDATES, 2);

        assertEquals(2, out.size());
        assertEquals(20L, out.get(0).getChunkId());
        assertEquals(0.95, out.get(0).getRelevanceScore(), 1e-9);
        assertEquals("路径20", out.get(0).getSectionPath());
        assertEquals(0.8, out.get(0).getVectorSimilarity(), 1e-9, "检索阶段的向量相似度必须透传");
        assertEquals(10L, out.get(1).getChunkId());
        server.verify();
    }

    @Test
    void resultsAreSortedByRelevanceDescendingEvenIfServerReturnsThemUnordered() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"output":{"results":[{"index":0,"relevance_score":0.2},{"index":2,"relevance_score":0.9},{"index":1,"relevance_score":0.5}]}}
            """, MediaType.APPLICATION_JSON));

        List<RerankResult> out = service.rerank("q", CANDIDATES, 3);

        assertEquals(List.of(30L, 20L, 10L), out.stream().map(RerankResult::getChunkId).toList());
    }

    @Test
    void topNIsClampedToCandidateCount() {
        server.expect(requestTo(URL))
            .andExpect(jsonPath("$.parameters.top_n").value(3))
            .andRespond(withSuccess("{\"output\":{\"results\":[{\"index\":0,\"relevance_score\":0.1}]}}",
                MediaType.APPLICATION_JSON));

        service.rerank("q", CANDIDATES, 100);
        server.verify();
    }

    @Test
    void outOfRangeIndexFailsLoudlyInsteadOfMismapping() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
            "{\"output\":{\"results\":[{\"index\":3,\"relevance_score\":0.9}]}}", MediaType.APPLICATION_JSON));
        RerankException e = assertThrows(RerankException.class, () -> service.rerank("q", CANDIDATES, 3));
        assertTrue(e.getMessage().contains("越界"), e.getMessage());
    }

    @Test
    void negativeIndexAlsoFails() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
            "{\"output\":{\"results\":[{\"index\":-1,\"relevance_score\":0.9}]}}", MediaType.APPLICATION_JSON));
        assertThrows(RerankException.class, () -> service.rerank("q", CANDIDATES, 3));
    }

    @Test
    void serverErrorIsNotSwallowed() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThrows(RerankException.class, () -> service.rerank("q", CANDIDATES, 3));
    }

    @Test
    void malformedResponseIsNotSwallowed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"code\":\"x\"}", MediaType.APPLICATION_JSON));
        assertThrows(RerankException.class, () -> service.rerank("q", CANDIDATES, 3));
    }

    @Test
    void candidatesWithoutAnyTextAreSkippedAndIndexesStillMapBackCorrectly() {
        // 存量数据可能 text/rerank_text 都为空（未回填）。空文档会让重排接口整体返回 400，必须在发送前剔除。
        List<HybridSearchResult> mixed = List.of(
            cand(1, "  ", null, 0.9),                 // 全空：跳过
            cand(2, "有内容的第二条", "t2", 0.8),
            cand(3, null, "", 0.7),                  // 全空：跳过
            cand(4, null, "仅有text的第四条", 0.6));

        server.expect(requestTo(URL))
            .andExpect(jsonPath("$.input.documents.length()").value(2))
            .andExpect(jsonPath("$.input.documents[0]").value("有内容的第二条"))
            .andExpect(jsonPath("$.input.documents[1]").value("仅有text的第四条"))
            .andExpect(jsonPath("$.parameters.top_n").value(2))
            // 接口返回的 index 是"发送列表"的下标：1 对应第四条，0 对应第二条
            .andRespond(withSuccess("""
                {"output":{"results":[{"index":1,"relevance_score":0.9},{"index":0,"relevance_score":0.4}]}}
                """, MediaType.APPLICATION_JSON));

        List<RerankResult> out = service.rerank("q", mixed, 5);

        assertEquals(List.of(4L, 2L), out.stream().map(RerankResult::getChunkId).toList());
        server.verify();
    }

    @Test
    void allCandidatesWithoutTextReturnsEmptyWithoutCallingTheApi() {
        List<HybridSearchResult> blank = List.of(cand(1, "", "", 0.9), cand(2, null, null, 0.8));
        assertTrue(service.rerank("q", blank, 5).isEmpty());
        server.verify();
    }

    @Test
    void emptyOrNullCandidatesReturnEmptyWithoutCallingTheApi() {
        assertTrue(service.rerank("q", List.of(), 5).isEmpty());
        assertTrue(service.rerank("q", null, 5).isEmpty());
        server.verify();
    }
}
