package com.dataplatform.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis 的消息存储服务
 * 负责将 Spring AI 的消息持久化到 MySQL messages 表
 */
@Slf4j
@Component("myBatisChatMemory")
@RequiredArgsConstructor
public class MyBatisChatMemory {

    private final MessageMapper messageMapper;

    public void add(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
        Long convId = parseConversationId(conversationId);

        for (org.springframework.ai.chat.messages.Message aiMsg : messages) {
            Message entity = convertToEntity(aiMsg, convId);
            messageMapper.insert(entity);
        }

        log.debug("[ChatMemory] Added {} messages to conversation {}", messages.size(), conversationId);
    }

    public List<org.springframework.ai.chat.messages.Message> get(String conversationId, int lastN) {
        Long convId = parseConversationId(conversationId);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, convId)
               .orderByDesc(Message::getId)
               .last("LIMIT " + lastN);

        List<Message> entities = messageMapper.selectList(wrapper);

        // 反转顺序（查询是倒序，需恢复正序）
        List<org.springframework.ai.chat.messages.Message> aiMessages = entities.stream()
            .map(this::convertToAIMessage)
            .collect(Collectors.toList());
        java.util.Collections.reverse(aiMessages);

        log.debug("[ChatMemory] Retrieved {} messages from conversation {}", aiMessages.size(), conversationId);
        return aiMessages;
    }

    public void clear(String conversationId) {
        Long convId = parseConversationId(conversationId);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, convId);
        messageMapper.delete(wrapper);

        log.info("[ChatMemory] Cleared conversation {}", conversationId);
    }

    /**
     * 解析会话 ID
     * conversationId 格式: "conv-{id}" 或直接传数字
     */
    private Long parseConversationId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            throw new IllegalArgumentException("conversationId cannot be null or empty");
        }

        // 支持两种格式：conv-123 或直接 123
        if (conversationId.startsWith("conv-")) {
            return Long.parseLong(conversationId.substring(5));
        } else {
            return Long.parseLong(conversationId);
        }
    }

    /**
     * 将 Spring AI Message 转换为数据库实体
     */
    private Message convertToEntity(org.springframework.ai.chat.messages.Message aiMsg, Long convId) {
        Message entity = new Message();
        entity.setConversationId(convId);

        // 根据消息类型提取内容和角色
        if (aiMsg instanceof UserMessage) {
            entity.setRole(MessageRole.USER);
            // UserMessage的内容通过text()或toString()获取
            entity.setContent(aiMsg.toString());
        } else if (aiMsg instanceof AssistantMessage) {
            entity.setRole(MessageRole.ASSISTANT);
            entity.setContent(aiMsg.toString());
        } else if (aiMsg instanceof SystemMessage) {
            entity.setRole(MessageRole.SYSTEM);
            entity.setContent(aiMsg.toString());
        } else if (aiMsg instanceof ToolResponseMessage toolMsg) {
            entity.setRole(MessageRole.TOOL);
            // 从第一个响应中提取内容
            var responses = toolMsg.getResponses();
            if (!responses.isEmpty()) {
                var firstResponse = responses.get(0);
                entity.setContent(firstResponse.responseData());
                entity.setToolCallId(firstResponse.id());
            }
        }

        return entity;
    }

    /**
     * 将数据库实体转换为 Spring AI Message
     */
    private org.springframework.ai.chat.messages.Message convertToAIMessage(Message entity) {
        return switch (entity.getRole()) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
            case TOOL -> {
                // ToolResponseMessage 需要 toolCallId, name 和 responseData
                yield new ToolResponseMessage(
                    List.of(new ToolResponseMessage.ToolResponse(
                        entity.getToolCallId() != null ? entity.getToolCallId() : "unknown",
                        "tool",  // tool name
                        entity.getContent() != null ? entity.getContent() : ""
                    ))
                );
            }
        };
    }
}
