package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobStatus;
import com.dataplatform.enums.JobType;
import com.dataplatform.enums.ScheduleFrequency;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 作业实体
 *
 * <p>版本管理说明：
 * - currentVersion：最新版本号（可能是草稿）
 * - onlineVersion：线上运行版本号，只在新创建、发布、回滚时更新
 * - 草稿态：currentVersion > onlineVersion
 * - 已发布：currentVersion = onlineVersion
 */
@Data
@TableName("etl_job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private String jobCnName;

    private JobType jobType;

    private Long sourceDatasourceId;

    private String sourceDatabase;

    private String sourceTable;

    private String targetDatabase;

    private String targetTable;

    private EngineType engineType;

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
     * 当前版本号（最新版本，可能是草稿）
     */
    private Integer currentVersion;

    /**
     * 线上运行版本号（只在新创建、发布、回滚时更新）
     */
    private Integer onlineVersion;

    /**
     * 作业状态
     */
    private JobStatus status;

    private String description;

    /**
     * 最后发布时间
     */
    private LocalDateTime lastPublishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建人ID
     */
    private Long createById;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 最后发布人
     */
    private String lastPublishBy;

    /**
     * 最后发布人ID
     */
    private Long lastPublishById;

    // ========== 辅助方法 ==========

    /**
     * 判断是否有未发布的变更
     */
    public boolean hasPendingChanges() {
        if (onlineVersion == null) {
            return currentVersion != null && currentVersion > 0;
        }
        return currentVersion != null && currentVersion > onlineVersion;
    }

    /**
     * 判断是否从未发布过
     */
    public boolean isNeverPublished() {
        return onlineVersion == null;
    }

    /**
     * 判断是否已发布
     */
    public boolean isPublished() {
        return onlineVersion != null && onlineVersion.equals(currentVersion);
    }
}
