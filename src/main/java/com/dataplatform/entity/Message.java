package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体
 */
@Data
@TableName("messages")
public class Message {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    @TableField("conversation_id")
    private Long conversationId;

    /**
     * 角色：user, assistant, tool
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容（assistant纯工具调用时可为空）
     */
    @TableField("content")
    private String content;

    /**
     * assistant消息带的工具调用申请（OpenAI格式）
     */
    @TableField("tool_calls")
    private String toolCalls;

    /**
     * tool消息对应的申请单ID
     */
    @TableField("tool_call_id")
    private String toolCallId;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
