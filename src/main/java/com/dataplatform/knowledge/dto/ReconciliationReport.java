package com.dataplatform.knowledge.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL -> PostgreSQL 双写对账报告。
 * id 列表只保留前 N 个用于展示，完整数量见各 *Count 字段。
 */
@Data
public class ReconciliationReport {

    /** 成功完成对比的 MySQL vectorized chunk 数（PG 查询失败的批次不计入） */
    private long checked;

    /** MySQL 标记 vectorized 但 PG 缺失 */
    private long missingCount;
    private List<Long> missingIds = new ArrayList<>();

    /** PG 有行但 text / rerank_text / section_path 与期望值不一致 */
    private long mismatchedCount;
    private List<Long> mismatchedIds = new ArrayList<>();

    /** PG 有、MySQL 无的 chunk_id（只报告，不自动删除） */
    private long orphanCount;
    private List<Long> orphanIds = new ArrayList<>();

    /** 已提交补偿（retryVectorization）的条数；补偿是异步的，此值不代表补偿已成功 */
    private long compensated;

    /** 超过单次补偿上限、留待下次处理的条数 */
    private long deferred;

    /** 处理失败（读 MySQL / 查 PG 异常）的批数 */
    private long failedBatches;
}
