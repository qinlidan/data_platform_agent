package com.dataplatform.knowledge.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.knowledge.dto.ReconciliationReport;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 双写数据对账任务：以 MySQL 为权威源，在应用层（不跨库 JOIN）校验 PostgreSQL knowledge_vectors。
 *
 * 1. missing    ：MySQL vectorized 但 PG 无此行        -> 补偿
 * 2. mismatched ：PG 的 text / rerank_text / section_path 与按 MySQL 当前数据重算的期望值不等 -> 补偿
 * 3. orphan     ：PG 有、MySQL 无（pending 状态的不算）  -> 只报告 + WARN，绝不自动删除
 *
 * 补偿通过 VectorizationService.retryVectorization（@Async）重新向量化并 upsert，
 * 因此 compensated 仅表示"已提交补偿"，不代表补偿已成功；下次对账会再次验证。
 */
@Component
@Slf4j
public class DataReconciliationTask {

    /** 报告中最多展示的 id 数，完整数量见 *Count */
    static final int MAX_LISTED_IDS = 100;
    /** 单次提交给 retryVectorization 的 id 数，避免一个异步任务过大 */
    static final int COMPENSATION_SUB_BATCH = 100;

    private static final String SELECT_VECTORS_SQL_PREFIX =
            "SELECT chunk_id, text, rerank_text, section_path FROM knowledge_vectors WHERE chunk_id IN (";
    private static final String SCAN_PG_IDS_SQL =
            "SELECT chunk_id FROM knowledge_vectors WHERE chunk_id > ? ORDER BY chunk_id LIMIT ?";

    private final KnowledgeChunkMapper chunkMapper;
    private final JdbcTemplate pgJdbcTemplate;
    private final VectorizationService vectorizationService;
    private final int batchSize;
    private final int maxCompensationPerRun;

    @Autowired
    public DataReconciliationTask(
            KnowledgeChunkMapper chunkMapper,
            @Qualifier("postgresqlJdbcTemplate") JdbcTemplate pgJdbcTemplate,
            VectorizationService vectorizationService,
            @Value("${knowledge.reconciliation.batch-size:500}") int batchSize,
            @Value("${knowledge.reconciliation.max-compensation:1000}") int maxCompensationPerRun) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        this.chunkMapper = chunkMapper;
        this.pgJdbcTemplate = pgJdbcTemplate;
        this.vectorizationService = vectorizationService;
        this.batchSize = batchSize;
        this.maxCompensationPerRun = Math.max(0, maxCompensationPerRun);
    }

    /** 每天凌晨 2 点执行 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledReconcile() {
        try {
            ReconciliationReport r = reconcile();
            log.info("数据对账完成: checked={}, missing={}, mismatched={}, orphan={}, compensated={}, deferred={}, failedBatches={}",
                    r.getChecked(), r.getMissingCount(), r.getMismatchedCount(), r.getOrphanCount(),
                    r.getCompensated(), r.getDeferred(), r.getFailedBatches());
        } catch (Exception e) {
            log.error("数据对账任务异常终止", e);
        }
    }

    public ReconciliationReport reconcile() {
        ReconciliationReport report = new ReconciliationReport();
        long[] quota = {maxCompensationPerRun};

        checkMysqlAgainstPg(report, quota);
        scanPgOrphans(report);

        if (report.getOrphanCount() > 0) {
            log.warn("发现 {} 条 PG 孤儿向量（MySQL 无对应 chunk），不会自动删除，请人工确认。前 {} 个: {}",
                    report.getOrphanCount(), MAX_LISTED_IDS, report.getOrphanIds());
        }
        return report;
    }

    // ---------------------------------------------------------------- MySQL -> PG

    private void checkMysqlAgainstPg(ReconciliationReport report, long[] quota) {
        long cursor = 0L;
        while (true) {
            List<KnowledgeChunk> chunks;
            try {
                chunks = chunkMapper.selectList(new QueryWrapper<KnowledgeChunk>()
                        .select("id", "doc_type", "scene_title", "questions", "answer", "section_path")
                        .eq("vectorize_status", "vectorized")
                        .gt("id", cursor)
                        .orderByAsc("id")
                        .last("LIMIT " + batchSize));
            } catch (Exception e) {
                // 游标无法前进，继续只会死循环：记录失败并结束 MySQL 侧扫描
                report.setFailedBatches(report.getFailedBatches() + 1);
                log.error("对账: 读取 MySQL 失败, cursor={}，终止 MySQL 侧扫描", cursor, e);
                return;
            }
            if (chunks == null || chunks.isEmpty()) {
                return;
            }
            cursor = chunks.get(chunks.size() - 1).getId();

            try {
                processBatch(chunks, report, quota);
            } catch (Exception e) {
                report.setFailedBatches(report.getFailedBatches() + 1);
                log.error("对账: 批次处理失败, ids {}~{}，继续下一批",
                        chunks.get(0).getId(), chunks.get(chunks.size() - 1).getId(), e);
            }

            if (chunks.size() < batchSize) {
                return;
            }
        }
    }

    private void processBatch(List<KnowledgeChunk> chunks, ReconciliationReport report, long[] quota) {
        List<Long> ids = chunks.stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
        Map<Long, Map<String, Object>> pgRows = fetchPgRows(ids);

        List<Long> toCompensate = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> row = pgRows.get(chunk.getId());
            if (row == null) {
                report.setMissingCount(report.getMissingCount() + 1);
                addCapped(report.getMissingIds(), chunk.getId());
                toCompensate.add(chunk.getId());
            } else if (isMismatched(chunk, row)) {
                report.setMismatchedCount(report.getMismatchedCount() + 1);
                addCapped(report.getMismatchedIds(), chunk.getId());
                toCompensate.add(chunk.getId());
            }
        }
        // 只有 PG 查询成功才算"已检查"
        report.setChecked(report.getChecked() + chunks.size());

        compensate(toCompensate, report, quota);
    }

    private Map<Long, Map<String, Object>> fetchPgRows(List<Long> ids) {
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(
                SELECT_VECTORS_SQL_PREFIX + placeholders + ")", ids.toArray());
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                byId.put(((Number) row.get("chunk_id")).longValue(), row);
            }
        }
        return byId;
    }

    private boolean isMismatched(KnowledgeChunk chunk, Map<String, Object> row) {
        return !sameText(vectorizationService.buildSearchText(chunk), row.get("text"))
                || !sameText(vectorizationService.buildRerankText(chunk), row.get("rerank_text"))
                || !sameText(chunk.getSectionPath(), row.get("section_path"));
    }

    /** null 与空串视为相同；除 trim 外不做其它归一化 */
    private static boolean sameText(Object expected, Object actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static String normalize(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private void compensate(List<Long> ids, ReconciliationReport report, long[] quota) {
        if (ids.isEmpty()) {
            return;
        }
        int allowed = (int) Math.min(quota[0], ids.size());
        quota[0] -= allowed;
        report.setDeferred(report.getDeferred() + (ids.size() - allowed));

        for (int from = 0; from < allowed; from += COMPENSATION_SUB_BATCH) {
            List<Long> sub = new ArrayList<>(ids.subList(from, Math.min(allowed, from + COMPENSATION_SUB_BATCH)));
            try {
                vectorizationService.retryVectorization(sub);
                report.setCompensated(report.getCompensated() + sub.size());
            } catch (Exception e) {
                // 提交失败的部分既没补偿也没推迟：计为失败批，下次对账仍会发现
                report.setFailedBatches(report.getFailedBatches() + 1);
                log.error("对账: 提交补偿失败, {} 条, 首个 id={}", sub.size(), sub.get(0), e);
            }
        }
    }

    // ---------------------------------------------------------------- PG -> MySQL (orphan)

    private void scanPgOrphans(ReconciliationReport report) {
        long cursor = 0L;
        while (true) {
            List<Long> pgIds;
            try {
                List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(SCAN_PG_IDS_SQL, cursor, batchSize);
                pgIds = rows == null ? Collections.emptyList() : rows.stream()
                        .map(r -> ((Number) r.get("chunk_id")).longValue())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                report.setFailedBatches(report.getFailedBatches() + 1);
                log.error("对账: 扫描 PG 失败, cursor={}，终止 orphan 检查", cursor, e);
                return;
            }
            if (pgIds.isEmpty()) {
                return;
            }
            cursor = pgIds.get(pgIds.size() - 1);

            try {
                // 只看"是否存在"，不看状态：MySQL 里是 pending 的不算 orphan
                Set<Long> existing = chunkMapper.selectBatchIds(pgIds).stream()
                        .map(KnowledgeChunk::getId)
                        .collect(Collectors.toSet());
                for (Long id : pgIds) {
                    if (!existing.contains(id)) {
                        report.setOrphanCount(report.getOrphanCount() + 1);
                        addCapped(report.getOrphanIds(), id);
                    }
                }
            } catch (Exception e) {
                report.setFailedBatches(report.getFailedBatches() + 1);
                log.error("对账: orphan 批次校验失败, ids {}~{}，继续下一批",
                        pgIds.get(0), pgIds.get(pgIds.size() - 1), e);
            }

            if (pgIds.size() < batchSize) {
                return;
            }
        }
    }

    private static void addCapped(List<Long> list, Long id) {
        if (list.size() < MAX_LISTED_IDS) {
            list.add(id);
        }
    }
}
