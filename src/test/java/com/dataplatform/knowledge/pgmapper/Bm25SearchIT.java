package com.dataplatform.knowledge.pgmapper;

import com.dataplatform.knowledge.dto.ChunkCandidate;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import com.dataplatform.knowledge.service.HybridSearchService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BM25 路的真实 PostgreSQL 集成测试（pg_jieba 分词 + GIN 索引 + 应用层打分）。
 *
 * 专用数据 chunk_id 9_100_000_001~005，结束后清理。用生造词 "zxqbm25" 作为查询词，避免与库内存量数据互相干扰：
 *  A: zxqbm25 ×3，文本很短           -> BM25 最高（tf 高 + 短）
 *  C: zxqbm25 ×3，混入 ~60 个填充词   -> 低于 A（长度归一）
 *  B: zxqbm25 ×1，混入 ~60 个填充词   -> 低于 C（词频）
 *  D: 无关内容                       -> 不应命中
 *  E: 另一 doc_type，短文，zxqbm25 ×1  -> 仅在不过滤 docType 时命中；与 B/C 的相对名次取决于长度归一，不断言
 */
@SpringBootTest
class Bm25SearchIT {

    private static final long A = 9_100_000_001L;
    private static final long B = 9_100_000_002L;
    private static final long C = 9_100_000_003L;
    private static final long D = 9_100_000_004L;
    private static final long E = 9_100_000_005L;
    private static final long[] ALL = {A, B, C, D, E};

    @Autowired private HybridSearchMapper mapper;
    @Autowired private HybridSearchService service;
    @Autowired private KnowledgeVectorRepository vectorRepository;

    @Autowired
    @Qualifier("postgresqlJdbcTemplate")
    private JdbcTemplate pg;

    private static String vec(int hot) {
        float[] v = new float[1024];
        v[hot] = 1f;
        return Arrays.toString(v);
    }

    private static String filler() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append(" fillw").append(String.format("%03d", i));
        }
        return sb.toString();
    }

    private void put(long id, String docType, String text) {
        LocalDateTime now = LocalDateTime.now();
        vectorRepository.upsert(id, vec((int) (id % 1000)), docType, "tunable", text, text, "IT / bm25 / " + id, now, now);
    }

    @BeforeEach
    void seed() {
        cleanup();
        put(A, "it_bm25", "zxqbm25 zxqbm25 zxqbm25 简短");
        put(B, "it_bm25", "zxqbm25" + filler());
        put(C, "it_bm25", "zxqbm25 zxqbm25 zxqbm25" + filler());
        put(D, "it_bm25", "完全无关的报表导出格式配置");
        put(E, "it_bm25_other", "zxqbm25 其他类型文档");
        service.invalidateBm25Stats();
    }

    @AfterEach
    void cleanup() {
        pg.update("delete from knowledge_vectors where chunk_id between 9100000001 and 9100000005");
        service.invalidateBm25Stats();
    }

    private static List<Long> seeded(List<HybridSearchResult> rs) {
        return rs.stream().map(HybridSearchResult::getChunkId)
                .filter(id -> Arrays.stream(ALL).anyMatch(x -> x == id)).toList();
    }

    @Test
    void queryTermsUseJiebaTokenizerAndDropBlankLexemes() {
        List<String> terms = mapper.queryTerms("内存溢出 Spark 的");
        assertThat(terms).contains("内存", "溢出", "spark").doesNotContain("", " ", "的");
        assertThat(terms).allSatisfy(t -> assertThat(t.trim()).isNotEmpty());
        assertThat(mapper.queryTerms("   ")).isEmpty();
    }

    @Test
    void corpusStatsAndDocFrequenciesMatchTheTable() {
        Long n = pg.queryForObject("select count(*) from knowledge_vectors", Long.class);
        var stats = mapper.corpusStats();
        assertThat(stats.getTotalDocs()).isEqualTo(n);
        assertThat(stats.getAvgDocLength()).isPositive();

        var dfs = mapper.docFrequencies(List.of("zxqbm25", "fillw007"));
        assertThat(dfs).hasSize(2);
        assertThat(dfs.stream().filter(d -> d.getLexeme().equals("zxqbm25")).findFirst().orElseThrow().getDocFreq())
                .isEqualTo(4);   // A B C E
        assertThat(dfs.stream().filter(d -> d.getLexeme().equals("fillw007")).findFirst().orElseThrow().getDocFreq())
                .isEqualTo(2);   // B C
    }

    @Test
    void bm25RanksByTermFrequencyAndLength() {
        List<HybridSearchResult> rs = service.bm25Search("zxqbm25", null, 20);

        List<Long> got = seeded(rs);
        assertThat(got).containsExactlyInAnyOrder(A, B, C, E).doesNotContain(D);
        assertThat(got.get(0)).isEqualTo(A);                                   // tf 最高且最短
        assertThat(got.indexOf(C)).isLessThan(got.indexOf(B));                 // 同长度，tf 3 > tf 1
        HybridSearchResult top = rs.stream().filter(r -> r.getChunkId() == A).findFirst().orElseThrow();
        assertThat(top.getScore()).isPositive();
        assertThat(top.getText()).startsWith("zxqbm25");
        assertThat(top.getSectionPath()).isEqualTo("IT / bm25 / " + A);
        assertThat(top.getDocType()).isEqualTo("it_bm25");
        // 分数严格递减
        double a = score(rs, A), c = score(rs, C), b = score(rs, B);
        assertThat(a).isGreaterThan(c);
        assertThat(c).isGreaterThan(b);
    }

    @Test
    void bm25AppliesDocTypeFilterAndTopK() {
        assertThat(seeded(service.bm25Search("zxqbm25", "it_bm25", 20))).containsExactly(A, C, B);
        assertThat(seeded(service.bm25Search("zxqbm25", "it_bm25_other", 20))).containsExactly(E);
        assertThat(service.bm25Search("zxqbm25", "it_bm25", 2)).hasSize(2);
    }

    @Test
    void bm25SurvivesHostileQueryText() {
        // 引号/反斜杠/tsquery 运算符不能让 SQL 报错
        for (String q : List.of("it's", "a\\b", "a & b | !c", "'; drop table x; --", "(", "<->", "*")) {
            service.bm25Search(q, null, 5);
        }
        assertThat(service.bm25Search("", null, 5)).isEmpty();
    }

    @Test
    void bm25MatchesOnAnyTermNotAllTerms() {
        // 查询里有一个库中不存在的词，OR 语义下仍应命中含 zxqbm25 的文档（keywordSearch 的 AND 语义会返回空）
        assertThat(seeded(service.bm25Search("zxqbm25 qqqnotexist", null, 20))).contains(A, C, B);
        assertThat(seeded(service.keywordSearch("zxqbm25 qqqnotexist", null, 20))).isEmpty();
    }

    @Test
    void keywordSearchIsStillAvailableAndUnchanged() {
        assertThat(seeded(service.keywordSearch("zxqbm25", null, 20))).contains(A, B, C, E);
    }

    @Test
    void searchCandidatesFindsByKeywordAndTruncatesText() {
        List<ChunkCandidate> cs = service.searchCandidates("zxqbm25", 50);
        assertThat(cs).extracting(ChunkCandidate::getChunkId).contains(A, B, C, E).doesNotContain(D);
        ChunkCandidate c = cs.stream().filter(x -> x.getChunkId() == C).findFirst().orElseThrow();
        assertThat(c.getDocType()).isEqualTo("it_bm25");
        assertThat(c.getSectionPath()).isEqualTo("IT / bm25 / " + C);
        assertThat(c.getText().length()).isLessThanOrEqualTo(120);
        assertThat(service.searchCandidates("zxqbm25", 2)).hasSize(2);
        assertThat(service.searchCandidates("", 20)).isEmpty();
    }

    @Test
    void statsCacheIsInvalidatedWhenCorpusChanges() {
        long before = service.bm25Search("zxqbm25", null, 50).size();
        pg.update("delete from knowledge_vectors where chunk_id = ?", E);
        service.invalidateBm25Stats();
        assertThat(service.bm25Search("zxqbm25", null, 50)).hasSize((int) before - 1);
    }

    private static double score(List<HybridSearchResult> rs, long id) {
        return rs.stream().filter(r -> r.getChunkId() == id).findFirst().orElseThrow().getScore();
    }
}
