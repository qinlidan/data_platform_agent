package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业版本实体
 */
@Data
@TableName("etl_job_version")
public class JobVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Integer version;

    private String sqlContent;

    private String sparkConfig;

    private Integer isPublished;

    private LocalDateTime publishTime;

    private String publishBy;

    private String changeLog;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
}
