package com.dataplatform.dto.version;

import com.dataplatform.enums.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本详情响应
 */
@Data
@Builder
@Schema(description = "版本详情")
public class VersionDetailVO {

    @Schema(description = "版本ID")
    private Long id;

    @Schema(description = "作业ID")
    private Long jobId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "版本状态")
    private VersionStatus status;

    @Schema(description = "状态描述")
    private String statusDesc;

    // ========== 作业配置快照 ==========

    @Schema(description = "作业名称")
    private String jobName;

    @Schema(description = "作业中文名")
    private String jobCnName;

    @Schema(description = "源数据源ID")
    private Long sourceDatasourceId;

    @Schema(description = "源数据源名称")
    private String sourceDatasourceName;

    @Schema(description = "源库名")
    private String sourceDatabase;

    @Schema(description = "源表名")
    private String sourceTable;

    @Schema(description = "目标库名")
    private String targetDatabase;

    @Schema(description = "目标表名")
    private String targetTable;

    @Schema(description = "SQL内容")
    private String sqlContent;

    @Schema(description = "Spark配置")
    private String sparkConfig;

    @Schema(description = "调度频率")
    private String scheduleFrequency;

    @Schema(description = "作业描述")
    private String description;

    // ========== 变更信息 ==========

    @Schema(description = "变更日志")
    private String changeLog;

    @Schema(description = "变更摘要")
    private String changeSummary;

    // ========== 操作人和时间 ==========

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "提交人")
    private String submitBy;

    @Schema(description = "发布人")
    private String publishBy;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    // ========== 回滚相关 ==========

    @Schema(description = "是否为回滚版本")
    private Boolean isRollback;

    @Schema(description = "从哪个版本回滚而来")
    private Integer rollbackFromVersion;

    // ========== 时间字段 ==========

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    // ========== 状态判断 ==========

    @Schema(description = "是否可以编辑")
    private Boolean canEdit;

    @Schema(description = "是否可以提交")
    private Boolean canSubmit;

    @Schema(description = "是否可以发布")
    private Boolean canPublish;

    @Schema(description = "是否可以回滚到此版本")
    private Boolean canRollbackTo;

    @Schema(description = "是否为当前版本")
    private Boolean isCurrent;

    @Schema(description = "是否为已发布版本")
    private Boolean isPublished;
}