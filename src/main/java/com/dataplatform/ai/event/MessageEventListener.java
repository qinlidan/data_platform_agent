package com.dataplatform.ai.event;

import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.entity.ToolCall;
import com.dataplatform.service.MessageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息事件监听器
 * 异步处理消息落表，不阻塞流式响应
 *
 * @author Data Platform Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventListener {

    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @Async
    @EventListener
    public void handleMessageSave(MessageSaveEvent event) {
        try {
            Message message = new Message();
            message.setConversationId(event.getConversationId());
            message.setRole(MessageRole.valueOf(event.getRole().toUpperCase()));
            message.setContent(event.getContent());

            // 处理 toolCalls JSON 字符串
            if (event.getToolCalls() != null) {
                try {
                    List<ToolCall> toolCalls = objectMapper.readValue(
                        event.getToolCalls(),
                        new TypeReference<List<ToolCall>>() {}
                    );
                    message.setToolCalls(toolCalls);
                } catch (Exception e) {
                    log.warn("[MessageEvent] 解析 toolCalls 失败，保存为 null: {}", event.getToolCalls(), e);
                }
            }

            message.setToolCallId(event.getToolCallId());

            messageService.save(message);

            log.debug("[MessageEvent] 消息已落表: conversationId={}, role={}",
                event.getConversationId(), event.getRole());
        } catch (Exception e) {
            log.error("[MessageEvent] 消息落表失败: conversationId={}, role={}",
                event.getConversationId(), event.getRole(), e);
        }
    }
}
