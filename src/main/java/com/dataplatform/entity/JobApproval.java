package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.enums.ApprovalStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业审批实体
 *
 * <p>用于需要审批流程的场景（发布、回滚等）
 */
@Data
@TableName("job_approval")
public class JobApproval {

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
     * 审批单号（自动生成，如：APV202610220001）
     */
    private String approvalNo;

    /**
     * 审批状态
     */
    private ApprovalStatus approvalStatus;

    /**
     * 申请人ID
     */
    private Long applicantId;

    /**
     * 申请人名称
     */
    private String applicantName;

    /**
     * 申请原因
     */
    private String applyReason;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人名称
     */
    private String approverName;

    /**
     * 审批意见
     */
    private String approvalOpinion;

    /**
     * 审批时间
     */
    private LocalDateTime approvalTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}