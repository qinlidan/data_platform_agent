package com.dataplatform.dto;

import lombok.Data;

/**
 * 聊天意图请求
 * 用于接收用户输入的自然语言消息并识别意图
 */
@Data
public class ChatIntentRequest {
    /**
     * 用户输入的消息
     */
    private String message;

    /**
     * 会话ID(可选)
     */
    private String conversationId;

    /**
     * 用户ID(可选)
     */
    private Long userId;
}
