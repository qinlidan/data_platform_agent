package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具调用审计日志实体
 *
 * <p>记录所有工具调用的完整信息，包括参数、结果、状态、耗时等，用于：
 * - 审计跟踪
 * - 问题排查
 * - 性能分析
 * - 权限审查
 */
@Data
@TableName("tool_audit_logs")
public class ToolAuditLog {

    /**
     * 审计日志 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 ID（如果来自 AI 对话）
     */
    @TableField("conversation_id")
    private Long conversationId;

    /**
     * 工具名称
     */
    @TableField("tool_name")
    private String toolName;

    /**
     * 调用参数（JSON）
     */
    @TableField("tool_input")
    private String toolInput;

    /**
     * 返回结果（截断至 1000 字符）
     */
    @TableField("tool_result")
    private String toolResult;

    /**
     * 状态：SUCCESS/FAILED/TIMEOUT/VALIDATION_FAILED/PERMISSION_DENIED/NEED_CONFIRMATION
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 耗时（毫秒）
     */
    @TableField("duration")
    private Long duration;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 用户角色列表（JSON）
     */
    @TableField("user_roles")
    private String userRoles;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
