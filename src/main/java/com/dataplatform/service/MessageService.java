package com.dataplatform.service;

import com.dataplatform.entity.Message;
import com.dataplatform.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageMapper messageMapper;

    /**
     * 保存用户消息
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @return 消息ID
     */
    public Long saveUserMessage(Long conversationId, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());

        messageMapper.insert(message);
        log.debug("[MessageService] Saved user message: id={}, conversationId={}", message.getId(), conversationId);

        return message.getId();
    }

    /**
     * 保存助手消息
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @param toolCalls      工具调用（JSON格式），可为null
     * @return 消息ID
     */
    public Long saveAssistantMessage(Long conversationId, String content, String toolCalls) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setContent(content);
        message.setToolCalls(toolCalls);
        message.setCreatedAt(LocalDateTime.now());

        messageMapper.insert(message);
        log.debug("[MessageService] Saved assistant message: id={}, conversationId={}, hasToolCalls={}",
                  message.getId(), conversationId, toolCalls != null);

        return message.getId();
    }

    /**
     * 保存工具结果消息
     *
     * @param conversationId 会话ID
     * @param toolCallId     工具调用ID
     * @param result         工具执行结果
     * @return 消息ID
     */
    public Long saveToolMessage(Long conversationId, String toolCallId, String result) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("tool");
        message.setContent(result);
        message.setToolCallId(toolCallId);
        message.setCreatedAt(LocalDateTime.now());

        messageMapper.insert(message);
        log.debug("[MessageService] Saved tool message: id={}, conversationId={}, toolCallId={}",
                  message.getId(), conversationId, toolCallId);

        return message.getId();
    }
}
