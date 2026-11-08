package com.dataplatform.knowledge.task;

import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.knowledge.dto.ReconciliationReport;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import com.dataplatform.knowledge.service.EmbeddingService;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.service.VectorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 纯 Mockito 单元测试（不启动 Spring 容器）。
 *
 * VectorizationService 用 spy(真实实例) 包装：buildSearchText / buildRerankText 走真实逻辑，
 * 这样"期望值"与向量化写入 PG 时的口径保持一致；retryVectorization 被 stub 掉并可 verify。
 *
 * PG 用一个内存 Map 模拟，由 queryForList 的 Answer 按 SQL 特征分发：
 *  - 含 "rerank_text"：按 chunk_id IN (...) 返回文本列
 *  - 含 "ORDER BY"：orphan 反向扫描的 id 游标分页，参数 (cursor, limit)
 */
class DataReconciliationTaskTest {

    private KnowledgeChunkMapper mapper;
    private JdbcTemplate jdbc;
    private VectorizationService vectorizationService;

    /** 模拟 MySQL 全表（含 pending 状态的行） */
    private final Map<Long, KnowledgeChunk> mysqlRows = new TreeMap<>();
    /** 模拟 PG knowledge_vectors：chunk_id -> {text, rerank_text, section_path} */
    private final Map<Long, Map<String, Object>> pgRows = new TreeMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(KnowledgeChunkMapper.class);
        jdbc = mock(JdbcTemplate.class);
        vectorizationService = spy(new VectorizationService(
                mock(KnowledgeChunkMapper.class), mock(EmbeddingService.class), mock(KnowledgeVectorRepository.class)));
        doNothing().when(vectorizationService).retryVectorization(anyList());

        // orphan 反向检查：MySQL 里"存在"（不论状态）的 id
        lenient().when(mapper.selectBatchIds(any())).thenAnswer(inv -> {
            java.util.Collection<?> ids = inv.getArgument(0);
            return mysqlRows.values().stream().filter(c -> ids.contains(c.getId())).collect(Collectors.toList());
        });
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> pgAnswer(inv.getArgument(0), (Object[]) inv.getRawArguments()[1]));
    }

    // ---------- fixtures ----------

    private KnowledgeChunk chunk(long id, String status) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(id);
        c.setDocType("spark调优");
        c.setSceneTitle("title" + id);
        c.setQuestions("q" + id);
        c.setAnswer("a" + id);
        c.setSectionPath("path/" + id);
        c.setVectorizeStatus(status);
        mysqlRows.put(id, c);
        return c;
    }

    private KnowledgeChunk vectorized(long id) {
        return chunk(id, "vectorized");
    }

    /** 往 PG 写一行与 MySQL 当前数据完全一致的副本 */
    private Map<String, Object> pgInSync(KnowledgeChunk c) {
        Map<String, Object> row = new HashMap<>();
        row.put("chunk_id", c.getId());
        row.put("text", vectorizationService.buildSearchText(c));
        row.put("rerank_text", vectorizationService.buildRerankText(c));
        row.put("section_path", c.getSectionPath());
        pgRows.put(c.getId(), row);
        return row;
    }

    private void pgOnly(long id) {
        Map<String, Object> row = new HashMap<>();
        row.put("chunk_id", id);
        row.put("text", "x");
        row.put("rerank_text", "x");
        row.put("section_path", "x");
        pgRows.put(id, row);
    }

    /** 按页顺序返回 MySQL 的 vectorized 分页结果；最后一页必须小于 batchSize 以终止循环 */
    @SafeVarargs
    private void mysqlPages(List<KnowledgeChunk>... pages) {
        List<List<KnowledgeChunk>> list = new ArrayList<>(Arrays.asList(pages));
        lenient().when(mapper.selectList(any())).thenAnswer(inv -> list.isEmpty() ? Collections.emptyList() : list.remove(0));
    }

    private List<Map<String, Object>> pgAnswer(String sql, Object[] args) {
        if (sql.contains("rerank_text")) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object a : args) {
                Map<String, Object> row = pgRows.get(((Number) a).longValue());
                if (row != null) out.add(new HashMap<>(row));
            }
            return out;
        }
        if (sql.contains("ORDER BY")) {
            long cursor = ((Number) args[0]).longValue();
            int limit = ((Number) args[1]).intValue();
            return pgRows.keySet().stream().filter(id -> id > cursor).limit(limit)
                    .map(id -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("chunk_id", id);
                        return m;
                    }).collect(Collectors.toList());
        }
        throw new IllegalStateException("unexpected sql: " + sql);
    }

    private DataReconciliationTask task(int batchSize, int maxCompensation) {
        return new DataReconciliationTask(mapper, jdbc, vectorizationService, batchSize, maxCompensation);
    }

    private List<Long> allRetriedIds() {
        ArgumentCaptor<List<Long>> cap = ArgumentCaptor.forClass(List.class);
        verify(vectorizationService, atLeastOnce()).retryVectorization(cap.capture());
        return cap.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    // ---------- tests ----------

    @Test
    void consistent_noCompensation() {
        KnowledgeChunk c1 = vectorized(1);
        KnowledgeChunk c2 = vectorized(2);
        pgInSync(c1);
        pgInSync(c2);
        mysqlPages(List.of(c1, c2), Collections.emptyList());

        ReconciliationReport r = task(2, 1000).reconcile();

        assertEquals(2, r.getChecked());
        assertEquals(0, r.getMissingCount());
        assertEquals(0, r.getMismatchedCount());
        assertEquals(0, r.getOrphanCount());
        assertEquals(0, r.getCompensated());
        assertEquals(0, r.getDeferred());
        assertEquals(0, r.getFailedBatches());
        verify(vectorizationService, never()).retryVectorization(anyList());
    }

    @Test
    void missing_isDetectedAndCompensated() {
        KnowledgeChunk c1 = vectorized(1);
        KnowledgeChunk c2 = vectorized(2);
        pgInSync(c1); // c2 在 PG 不存在
        mysqlPages(List.of(c1, c2));

        ReconciliationReport r = task(500, 1000).reconcile();

        assertEquals(List.of(2L), r.getMissingIds());
        assertEquals(1, r.getMissingCount());
        assertEquals(0, r.getMismatchedCount());
        assertEquals(1, r.getCompensated());
        assertEquals(List.of(2L), allRetriedIds());
    }

    @Test
    void textMismatch_isDetected() {
        KnowledgeChunk c1 = vectorized(1);
        pgInSync(c1).put("text", "stale text");
        mysqlPages(List.of(c1));

        ReconciliationReport r = task(500, 1000).reconcile();

        assertEquals(List.of(1L), r.getMismatchedIds());
        assertEquals(0, r.getMissingCount());
        assertEquals(List.of(1L), allRetriedIds());
    }

    @Test
    void sectionPathMismatch_withSameText_isDetected() {
        KnowledgeChunk c1 = vectorized(1);
        pgInSync(c1).put("section_path", "old/path");
        mysqlPages(List.of(c1));

        ReconciliationReport r = task(500, 1000).reconcile();

        assertEquals(List.of(1L), r.getMismatchedIds());
        assertEquals(1, r.getCompensated());
    }

    @Test
    void rerankTextMismatch_isDetected() {
        KnowledgeChunk c1 = vectorized(1);
        pgInSync(c1).put("rerank_text", "stale rerank");
        mysqlPages(List.of(c1));

        ReconciliationReport r = task(500, 1000).reconcile();

        assertEquals(List.of(1L), r.getMismatchedIds());
        assertEquals(1, r.getCompensated());
    }

    @Test
    void nullAndEmptyString_areTreatedAsEqual() {
        KnowledgeChunk c1 = vectorized(1);
        c1.setSectionPath(null);
        pgInSync(c1).put("section_path", ""); // MySQL null vs PG ""
        KnowledgeChunk c2 = vectorized(2);
        c2.setSectionPath("");
        pgInSync(c2).put("section_path", null); // MySQL "" vs PG null
        mysqlPages(List.of(c1, c2));

        ReconciliationReport r = task(500, 1000).reconcile();

        assertEquals(0, r.getMismatchedCount());
        verify(vectorizationService, never()).retryVectorization(anyList());
    }

    @Test
    void orphan_isReportedButNeverCompensatedOrDeleted() {
        KnowledgeChunk c1 = vectorized(1);
        pgInSync(c1);
        KnowledgeChunk pending = chunk(50, "pending_vectorization"); // MySQL 有行但 pending：不算 orphan
        pgInSync(pending);
        pgOnly(99); // MySQL 里根本没有
        mysqlPages(List.of(c1));

        ReconciliationReport r = task(500, 1000).reconcile();

        assertEquals(List.of(99L), r.getOrphanIds());
        assertEquals(1, r.getOrphanCount());
        assertEquals(0, r.getCompensated());
        verify(vectorizationService, never()).retryVectorization(anyList());
        // 不得对 PG 做任何写操作
        verify(jdbc, never()).update(anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void orphanScan_pagesAcrossMultipleBatches() {
        mysqlPages(Collections.emptyList());
        pgOnly(10);
        pgOnly(20);
        pgOnly(30);

        ReconciliationReport r = task(2, 1000).reconcile(); // 批大小 2 => 需要翻两页

        assertEquals(List.of(10L, 20L, 30L), r.getOrphanIds());
        assertEquals(3, r.getOrphanCount());
    }

    @Test
    void overCompensationCap_remainderIsDeferred() {
        List<KnowledgeChunk> page = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            page.add(vectorized(i)); // 全部 missing
        }
        mysqlPages(page);

        ReconciliationReport r = task(500, 3).reconcile();

        assertEquals(5, r.getMissingCount());
        assertEquals(3, r.getCompensated());
        assertEquals(2, r.getDeferred());
        assertEquals(List.of(1L, 2L, 3L), allRetriedIds());
    }

    @Test
    void pgQueryFailureOnOneBatch_continuesWithNextBatches() {
        KnowledgeChunk c1 = vectorized(1);
        KnowledgeChunk c2 = vectorized(2);
        KnowledgeChunk c3 = vectorized(3);
        KnowledgeChunk c4 = vectorized(4);
        pgInSync(c1);
        pgInSync(c2);
        pgInSync(c3);
        pgInSync(c4).put("text", "stale"); // 第二批里有不一致
        mysqlPages(List.of(c1, c2), List.of(c3, c4), Collections.emptyList());

        // 第一次"取文本列"的 PG 查询抛异常，之后恢复正常
        AtomicInteger textQueries = new AtomicInteger();
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("rerank_text") && textQueries.getAndIncrement() == 0) {
                throw new org.springframework.dao.DataAccessResourceFailureException("pg down");
            }
            return pgAnswer(sql, (Object[]) inv.getRawArguments()[1]);
        });

        ReconciliationReport r = task(2, 1000).reconcile();

        assertEquals(1, r.getFailedBatches());
        assertEquals(2, r.getChecked()); // 失败批不计入 checked
        assertEquals(0, r.getMissingCount()); // 失败批不能被误判为 missing
        assertEquals(List.of(4L), r.getMismatchedIds()); // 后续批次照常对账
        assertEquals(List.of(4L), allRetriedIds());
        assertTrue(r.getFailedBatches() > 0);
    }
}
