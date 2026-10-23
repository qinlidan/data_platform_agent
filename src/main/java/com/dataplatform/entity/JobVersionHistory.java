package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.enums.VersionAction;
import com.dataplatform.enums.VersionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业版本操作历史实体
 *
 * <p>记录每个版本的所有状态变更操作，用于审计追踪。
 */
@Data
@Builder
@TableName("job_version_history")
public class JobVersionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 作业ID
     */
    private Long jobId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 操作类型
     */
    private VersionAction action;

    /**
     * 操作描述
     */
    private String actionDesc;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;

    /**
     * 原状态
     */
    private VersionStatus fromStatus;

    /**
     * 目标状态
     */
    private VersionStatus toStatus;

    /**
     * 备注（审核意见、回滚原因等）
     */
    private String remark;

    /**
     * 关联版本号（回滚时记录目标版本）
     */
    private Integer relatedVersion;

    /**
     * 操作时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}