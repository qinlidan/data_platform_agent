package com.dataplatform.ai.event;

import com.dataplatform.entity.Conversation;
import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.entity.ToolCall;
import com.dataplatform.service.ConversationService;
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
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @Async
    @EventListener
    public void handleMessageSave(MessageSaveEvent event) {
        log.info("[MessageEvent] ========== 收到消息保存事件 ==========");
        log.info("[MessageEvent] conversationId={}, role={}, contentLength={}",
            event.getConversationId(), event.getRole(),
            event.getContent() != null ? event.getContent().length() : 0);

        // 验证 conversationId 是否存在（防止外键约束失败）
        Conversation conversation = conversationService.getById(event.getConversationId());
        if (conversation == null) {
            log.error("[MessageEvent] ❌ Conversation不存在，跳过保存消息: conversationId={}", event.getConversationId());
            return;
        }

        try {
            Message message = new Message();
            message.setConversationId(event.getConversationId());
            message.setRole(MessageRole.valueOf(event.getRole().toUpperCase()));
            message.setContent(event.getContent());

            // 兜底：如果 MyBatis-Plus 自动填充失败，手动设置时间
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(java.time.LocalDateTime.now());
            }

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

//            log.info("[MessageEvent] 准备调用 messageService.save()...");
            boolean saved = messageService.save(message);
            log.info("[MessageEvent] messageService.save() 返回: {}, 消息ID: {}", saved, message.getId());

            log.info("[MessageEvent] ✅ 消息已落表: conversationId={}, role={}, messageId={}",
                event.getConversationId(), event.getRole(), message.getId());
        } catch (Exception e) {
            log.error("[MessageEvent] ❌ 消息落表失败: conversationId={}, role={}",
                event.getConversationId(), event.getRole(), e);
        }
    }
}
