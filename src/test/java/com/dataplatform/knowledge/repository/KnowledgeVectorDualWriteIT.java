package com.dataplatform.knowledge.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 PostgreSQL 验证：upsert 写入的 text / rerank_text / section_path 能读回，
 * text_tsv 由生成列自动维护，且支持关键词（含版本号 3.2）命中。
 * 使用专用 chunk_id=987654321，结束后清理，不污染现有数据。
 */
@SpringBootTest
class KnowledgeVectorDualWriteIT {

    private static final long TEST_ID = 987654321L;

    @Autowired private KnowledgeVectorRepository vectorRepository;

    @Autowired
    @Qualifier("postgresqlJdbcTemplate")
    private JdbcTemplate pg;

    @Test
    void upsertWritesNewColumnsAndGeneratedTsvIsSearchable() {
        try {
            float[] vec = new float[1024];
            vec[0] = 1f;
            LocalDateTime now = LocalDateTime.now();

            vectorRepository.upsert(TEST_ID, Arrays.toString(vec), "spark调优", "tunable",
                "spark调优 作业OOM Spark 3.2 内存溢出 增加executor内存",
                "作业OOM Spark 3.2 内存溢出 增加executor内存",
                "Spark调优 / 内存 / OOM", now, now);

            Map<String, Object> row = pg.queryForMap(
                "select text, rerank_text, section_path, doc_type from knowledge_vectors where chunk_id = ?", TEST_ID);
            assertEquals("spark调优 作业OOM Spark 3.2 内存溢出 增加executor内存", row.get("text"));
            assertEquals("作业OOM Spark 3.2 内存溢出 增加executor内存", row.get("rerank_text"));
            assertEquals("Spark调优 / 内存 / OOM", row.get("section_path"));

            // 生成列被自动维护
            String tsv = pg.queryForObject("select text_tsv::text from knowledge_vectors where chunk_id = ?",
                String.class, TEST_ID);
            assertNotNull(tsv);
            assertFalse(tsv.isBlank());

            // 关键词路能命中（plainto_tsquery 对带版本号的整句也适用）
            List<Long> hits = pg.queryForList(
                "select chunk_id from knowledge_vectors where text_tsv @@ plainto_tsquery('jiebacfg', ?)",
                Long.class, "内存溢出");
            assertTrue(hits.contains(TEST_ID), "关键词 '内存溢出' 应命中新写入的 chunk，实际: " + hits);

            // 二次 upsert 只更新 section_path（目录调整、正文不变）也必须落库
            vectorRepository.upsert(TEST_ID, Arrays.toString(vec), "spark调优", "tunable",
                "spark调优 作业OOM Spark 3.2 内存溢出 增加executor内存",
                "作业OOM Spark 3.2 内存溢出 增加executor内存",
                "Spark调优 / 新章节 / OOM", now, now);
            String path = pg.queryForObject("select section_path from knowledge_vectors where chunk_id = ?",
                String.class, TEST_ID);
            assertEquals("Spark调优 / 新章节 / OOM", path);
        } finally {
            pg.update("delete from knowledge_vectors where chunk_id = ?", TEST_ID);
        }
    }
}
