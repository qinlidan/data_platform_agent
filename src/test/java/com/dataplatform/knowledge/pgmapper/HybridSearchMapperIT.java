package com.dataplatform.knowledge.pgmapper;

import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HybridSearchMapper 的真实 PostgreSQL 集成测试。
 * Mockito 测试只能证明 Service 转发，证明不了 SQL 能在 PG 上执行，这里补上。
 *
 * 造 3 条专用数据（chunk_id 9_000_000_001~003），结束后清理，不污染现有数据：
 *  A: 向量=e0，文本含 "Spark 3.2 OOM 内存溢出"            -> 向量路与关键词路都应命中
 *  B: 向量=e1（与 e0 正交），文本含 "Spark 3.2 内存溢出"    -> 仅关键词路命中
 *  C: 向量=e0 附近，文本为无关内容 "报表导出格式配置"        -> 仅向量路命中
 */
@SpringBootTest
class HybridSearchMapperIT {

    private static final long A = 9_000_000_001L;
    private static final long B = 9_000_000_002L;
    private static final long C = 9_000_000_003L;

    @Autowired private HybridSearchMapper mapper;
    @Autowired private KnowledgeVectorRepository vectorRepository;

    @Autowired
    @Qualifier("postgresqlJdbcTemplate")
    private JdbcTemplate pg;

    private static String vec(int hotIndex, float hot, int coldIndex, float cold) {
        float[] v = new float[1024];
        v[hotIndex] = hot;
        v[coldIndex] = cold;
        return Arrays.toString(v);
    }

    @BeforeEach
    void seed() {
        cleanup();
        LocalDateTime now = LocalDateTime.now();
        vectorRepository.upsert(A, vec(0, 1f, 2, 0f), "it_doc", "tunable",
            "it_doc 作业OOM Spark 3.2 OOM 内存溢出 增加executor内存", "作业OOM Spark 3.2 OOM 内存溢出 增加executor内存",
            "IT / 内存 / OOM", now, now);
        vectorRepository.upsert(B, vec(1, 1f, 2, 0f), "it_doc", "tunable",
            "it_doc 内存告警 Spark 3.2 内存溢出 调整堆外内存", "内存告警 Spark 3.2 内存溢出 调整堆外内存",
            "IT / 内存 / 告警", now, now);
        vectorRepository.upsert(C, vec(0, 0.9f, 2, 0.1f), "it_other", "guide",
            "it_other 导出 报表导出格式配置", "导出 报表导出格式配置",
            "IT / 报表", now, now);
    }

    @AfterEach
    void cleanup() {
        pg.update("delete from knowledge_vectors where chunk_id in (?,?,?)", A, B, C);
    }

    private static List<Long> ids(List<HybridSearchResult> rs) {
        return rs.stream().map(HybridSearchResult::getChunkId).toList();
    }

    @Test
    void keywordSearchRunsOnPostgresAndUsesChineseTokenizer() {
        // "内存溢出" 需要中文分词才能命中 A、B；C 不含该词
        List<HybridSearchResult> rs = mapper.keywordSearch("内存溢出", null, 20);

        assertTrue(ids(rs).contains(A), "A 应命中: " + ids(rs));
        assertTrue(ids(rs).contains(B), "B 应命中: " + ids(rs));
        assertFalse(ids(rs).contains(C), "C 不应命中: " + ids(rs));
        HybridSearchResult a = rs.stream().filter(r -> r.getChunkId() == A).findFirst().orElseThrow();
        assertEquals("IT / 内存 / OOM", a.getSectionPath());
        assertNotNull(a.getText());
        assertNotNull(a.getRerankText());
    }

    @Test
    void keywordSearchMatchesVersionNumber() {
        // 验收 13.1：带版本号的查询能命中
        List<HybridSearchResult> rs = mapper.keywordSearch("Spark 3.2", null, 20);
        assertTrue(ids(rs).containsAll(List.of(A, B)), "版本号 3.2 应命中 A、B: " + ids(rs));
    }

    @Test
    void vectorSearchRunsOnPostgresAndRanksByCosine() {
        List<HybridSearchResult> rs = mapper.vectorSearch(vec(0, 1f, 2, 0f), null, 50);

        List<Long> got = ids(rs).stream().filter(id -> id == A || id == B || id == C).toList();
        // 查询向量 = e0：A 完全相同 > C 接近 > B 正交
        assertEquals(List.of(A, C, B), got, "排序应为 A > C > B");
        HybridSearchResult top = rs.stream().filter(r -> r.getChunkId() == A).findFirst().orElseThrow();
        assertEquals(1.0, top.getScore(), 1e-3, "完全相同的向量相似度应≈1");
    }

    @Test
    void hybridSearchFusesBothRoutesAndExposesVectorSimilarity() {
        List<HybridSearchResult> rs = mapper.hybridSearch(vec(0, 1f, 2, 0f), "内存溢出", null, 10);

        List<Long> got = ids(rs);
        assertTrue(got.contains(A), "A 两路都命中，必须在结果里: " + got);
        assertTrue(got.contains(B), "B 仅关键词路命中，也应进入融合结果: " + got);
        assertTrue(got.contains(C), "C 仅向量路命中，也应进入融合结果: " + got);
        // 两路都命中的 A 的 RRF 分数应高于只命中单路的 B、C
        double a = score(rs, A), b = score(rs, B), c = score(rs, C);
        assertTrue(a > b && a > c, "A 的 RRF 分应最高: a=" + a + " b=" + b + " c=" + c);

        HybridSearchResult ra = rs.stream().filter(r -> r.getChunkId() == A).findFirst().orElseThrow();
        assertNotNull(ra.getVectorRank());
        assertNotNull(ra.getKeywordRank());
        // B 的向量与查询正交，向量路排名必然落后于 A；关键词路有名次。
        // 注意不能断言 B 的 vectorRank 为 null：向量路是"取最近的 50 条"，库里存量数据少于 50 条时 B 也会入围。
        HybridSearchResult rb = rs.stream().filter(r -> r.getChunkId() == B).findFirst().orElseThrow();
        assertNotNull(rb.getKeywordRank());
        if (rb.getVectorRank() != null) {
            assertTrue(rb.getVectorRank() > ra.getVectorRank(), "B 的向量名次应落后于 A");
        }
        // C 与关键词完全无关，关键词路不应有名次（plainto_tsquery('内存溢出') 不会命中 '报表导出格式配置'）
        HybridSearchResult rc = rs.stream().filter(r -> r.getChunkId() == C).findFirst().orElseThrow();
        assertNull(rc.getKeywordRank(), "C 不含关键词，关键词路不应命中");
        // 拒答阈值依赖的向量相似度必须可取到（plan: vectorSimilarity < 0.6 拒答）
        assertNotNull(ra.getVectorSimilarity(), "hybrid 结果必须带 vectorSimilarity，拒答阈值要用它");
        assertEquals(1.0, ra.getVectorSimilarity(), 1e-3);
    }

    @Test
    void docTypeFilterIsAppliedToAllThreeRoutes() {
        String v = vec(0, 1f, 2, 0f);
        assertTrue(ids(mapper.vectorSearch(v, "it_doc", 50)).stream().noneMatch(id -> id == C));
        assertTrue(ids(mapper.keywordSearch("内存溢出", "it_other", 50)).stream().noneMatch(id -> id == A || id == B));
        List<Long> hy = ids(mapper.hybridSearch(v, "内存溢出", "it_doc", 20));
        assertTrue(hy.contains(A) && hy.contains(B));
        assertFalse(hy.contains(C), "docType=it_doc 时不应出现 it_other 的 C: " + hy);
    }

    @Test
    void queryWithTsqueryOperatorsOrPunctuationDoesNotBlowUp() {
        // plainto_tsquery 对任意用户输入安全；to_tsquery 遇到 & | ! ' 会直接抛语法错误
        for (String q : new String[]{"Spark & (内存", "a | b !c", "it's: 'quoted'", "   ", "内存溢出???"}) {
            mapper.keywordSearch(q, null, 5);
            mapper.hybridSearch(vec(0, 1f, 2, 0f), q, null, 5);
        }
    }

    private static double score(List<HybridSearchResult> rs, long id) {
        return rs.stream().filter(r -> r.getChunkId() == id).findFirst().orElseThrow().getRrfScore();
    }
}
