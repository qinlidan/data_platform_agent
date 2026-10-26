package com.dataplatform.ai.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 消息保存事件
 * 在用户提问、助手回复、工具调用完成时触发，异步落表
 *
 * @author Data Platform Team
 */
@Getter
public class MessageSaveEvent extends ApplicationEvent {

    private final Long conversationId;
    private final String role;  // user / assistant / tool
    private final String content;
    private final String toolCalls;  // JSON 格式的工具调用（仅 assistant）
    private final String toolCallId;  // 工具消息对应的 ID（仅 tool）

    public MessageSaveEvent(Object source, Long conversationId, String role, String content) {
        this(source, conversationId, role, content, null, null);
    }

    public MessageSaveEvent(Object source, Long conversationId, String role, String content,
                            String toolCalls, String toolCallId) {
        super(source);
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }
}
