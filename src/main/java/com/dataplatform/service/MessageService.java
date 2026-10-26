package com.dataplatform.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.entity.ToolCall;
import com.dataplatform.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息服务
 */
@Service
@Slf4j
public class MessageService extends ServiceImpl<MessageMapper, Message> {

    /**
     * 保存用户消息（异步）
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @return 消息ID
     */
    @Async
    public void saveUserMessage(Long conversationId, String content) {
        try {
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setRole(MessageRole.USER);
            message.setContent(content);

            save(message);
            log.debug("[MessageService] Saved user message: id={}, conversationId={}", message.getId(), conversationId);
        } catch (Exception e) {
            log.error("[MessageService] Failed to save user message (non-blocking)", e);
        }
    }

    /**
     * 保存助手消息（异步）
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @param toolCalls      工具调用列表，可为null
     * @return 消息ID
     */
    @Async
    public void saveAssistantMessage(Long conversationId, String content, List<ToolCall> toolCalls) {
        try {
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setRole(MessageRole.ASSISTANT);
            message.setContent(content);
            message.setToolCalls(toolCalls);

            save(message);
            log.debug("[MessageService] Saved assistant message: id={}, conversationId={}, hasToolCalls={}",
                      message.getId(), conversationId, toolCalls != null && !toolCalls.isEmpty());
        } catch (Exception e) {
            log.error("[MessageService] Failed to save assistant message (non-blocking)", e);
        }
    }

    /**
     * 保存工具结果消息（异步）
     *
     * @param conversationId 会话ID
     * @param toolCallId     工具调用ID
     * @param result         工具执行结果
     * @return 消息ID
     */
    @Async
    public void saveToolMessage(Long conversationId, String toolCallId, String result) {
        try {
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setRole(MessageRole.TOOL);
            message.setContent(result);
            message.setToolCallId(toolCallId);

            save(message);
            log.debug("[MessageService] Saved tool message: id={}, conversationId={}, toolCallId={}",
                      message.getId(), conversationId, toolCallId);
        } catch (Exception e) {
            log.error("[MessageService] Failed to save tool message (non-blocking)", e);
        }
    }

    /**
     * 获取会话的所有消息
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    public List<Message> listByConversationId(Long conversationId) {
        return lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreatedAt)
                .list();
    }

    /**
     * 删除会话的所有消息
     *
     * @param conversationId 会话ID
     */
    public void deleteByConversationId(Long conversationId) {
        lambdaUpdate()
                .eq(Message::getConversationId, conversationId)
                .remove();
        log.debug("[MessageService] Deleted messages for conversation: id={}", conversationId);
    }
}
