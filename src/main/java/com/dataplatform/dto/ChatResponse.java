package com.dataplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 对话响应")
public class ChatResponse {

    @Schema(description = "AI 回复内容")
    private String reply;

    @Schema(description = "会话ID")
    private String conversationId;

    @Schema(description = "消息ID")
    private String messageId;

    public static ChatResponse of(String reply, String conversationId) {
        return new ChatResponse(reply, conversationId, null);
    }
}
