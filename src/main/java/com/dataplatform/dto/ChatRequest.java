package com.dataplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 对话请求
 */
@Data
@Schema(description = "AI 对话请求")
public class ChatRequest {

    @Schema(description = "用户消息", example = "你好，帮我创建一个作业")
    private String message;

    @Schema(description = "会话ID（用于多轮对话）", example = "conv_123456")
    private String conversationId;

    @Schema(description = "用户ID", example = "1")
    private Long userId;
}
