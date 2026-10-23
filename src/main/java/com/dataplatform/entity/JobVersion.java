package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.enums.ScheduleFrequency;
import com.dataplatform.enums.VersionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业版本实体（完整快照）
 *
 * <p>设计原则：
 * 1. 每个版本保存完整的作业配置快照（包括数据源名称）
 * 2. 即使数据源被删除，历史版本仍可查看和对比
 * 3. 版本不可修改（immutable），只能创建新版本
 */
@Data
@TableName("etl_job_version")
public class JobVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 作业ID
     */
    private Long jobId;

    /**
     * 版本号（从1开始递增）
     */
    private Integer version;

    /**
     * 版本状态
     */
    private VersionStatus status;

    // ========== 作业配置快照 ==========

    /**
     * 作业名称（快照）
     */
    private String jobName;

    /**
     * 作业中文名（快照）
     */
    private String jobCnName;

    /**
     * 源数据源ID
     */
    private Long sourceDatasourceId;

    /**
     * 源数据源名称（快照，防止数据源删除后无法查看）
     */
    private String sourceDatasourceName;

    /**
     * 源库名
     */
    private String sourceDatabase;

    /**
     * 源表名
     */
    private String sourceTable;

    /**
     * 目标库名
     */
    private String targetDatabase;

    /**
     * 目标表名
     */
    private String targetTable;

    /**
     * SQL内容
     */
    private String sqlContent;

    /**
     * Spark配置（JSON格式）
     */
    private String sparkConfig;

    /**
     * 调度频率：DAILY/WEEKLY/MONTHLY/YEARLY
     */
    private ScheduleFrequency scheduleFrequency;

    /**
     * 是否监控该作业
     */
    @TableField("is_monitored")
    private Boolean monitored;

    /**
     * 作业描述
     */
    private String description;

    // ========== 变更信息 ==========

    /**
     * 变更日志（用户填写）
     */
    private String changeLog;

    /**
     * 变更摘要（系统自动生成）
     */
    private String changeSummary;

    // ========== 操作人和时间 ==========

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 提交人
     */
    private String submitBy;

    /**
     * 发布人
     */
    private String publishBy;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    // ========== 回滚相关 ==========

    /**
     * 是否为回滚版本（0-否，1-是）
     */
    private Integer isRollback;

    /**
     * 从哪个版本回滚而来
     */
    private Integer rollbackFromVersion;

    // ========== 标准字段 ==========

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

    // ========== 辅助方法 ==========

    /**
     * 判断是否可以编辑
     */
    public boolean canEdit() {
        return status != null && status.canEdit();
    }

    /**
     * 判断是否可以提交
     */
    public boolean canSubmit() {
        return status != null && status.canSubmit();
    }

    /**
     * 判断是否可以发布
     */
    public boolean canPublish() {
        return status != null && status.canPublish();
    }

    /**
     * 判断是否可以回滚到此版本
     */
    public boolean canRollbackTo() {
        return status != null && status.canRollbackTo();
    }
}
