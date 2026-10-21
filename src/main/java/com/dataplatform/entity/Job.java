package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.enums.EngineType;
import com.dataplatform.enums.JobStatus;
import com.dataplatform.enums.JobType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业实体
 */
@Data
@TableName("etl_job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobCode;

    private String jobName;

    private JobType jobType;

    private Long sourceDatasourceId;

    private String sourceDatabase;

    private String sourceTable;

    private String targetDatabase;

    private String targetTable;

    private EngineType engineType;

    private String scheduleFrequency;

    private Integer currentVersion;

    private Integer publishedVersion;

    private JobStatus status;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
