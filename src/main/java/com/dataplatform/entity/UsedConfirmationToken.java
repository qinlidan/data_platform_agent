package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已用确认令牌实体
 */
@Data
@TableName("used_confirmation_tokens")
public class UsedConfirmationToken {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Token 唯一标识（JWT jti）
     */
    @TableField("jti")
    private String jti;

    /**
     * 绑定的工具名
     */
    @TableField("tool_name")
    private String toolName;

    /**
     * 绑定的参数（JSON格式）
     */
    @TableField("tool_params")
    private String toolParams;

    /**
     * 使用时间
     */
    @TableField(value = "used_at", fill = FieldFill.INSERT)
    private LocalDateTime usedAt;

    /**
     * 过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
