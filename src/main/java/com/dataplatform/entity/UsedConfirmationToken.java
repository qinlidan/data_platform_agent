package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 已用确认令牌实体（防止重放攻击）
 */
@Data
@TableName(value = "used_confirmation_tokens", autoResultMap = true)
public class UsedConfirmationToken {

    /**
     * 自增 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Token 唯一 ID（JWT 的 jti 字段）
     */
    private String jti;

    /**
     * 绑定的工具名
     */
    private String toolName;

    /**
     * 绑定的参数（JSON 格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> toolParams;

    /**
     * 使用时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedAt;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
