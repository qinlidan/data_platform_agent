package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 消息实体
 */
@Data
@TableName(value = "messages", autoResultMap = true)
public class Message {

    /**
     * 消息 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话 ID
     */
    private Long conversationId;

    /**
     * 消息角色
     */
    private MessageRole role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 工具调用申请（JSON 格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ToolCall> toolCalls;

    /**
     * 工具消息对应的申请单 ID
     */
    private String toolCallId;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
