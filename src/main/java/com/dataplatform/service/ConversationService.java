package com.dataplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.entity.Conversation;
import com.dataplatform.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 会话服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationMapper conversationMapper;

    /**
     * 获取或创建会话
     * 当前实现：每次都创建新会话（简化逻辑，后续可优化为复用进行中的会话）
     *
     * @param userId 用户ID
     * @return 会话ID
     */
    public Long getOrCreateConversation(String userId) {
        // 当前策略：每次对话都创建新会话
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setStatus("active");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        conversationMapper.insert(conversation);
        log.debug("[ConversationService] Created new conversation: id={}, userId={}", conversation.getId(), userId);

        return conversation.getId();
    }

    /**
     * 结束会话
     *
     * @param conversationId 会话ID
     */
    public void endConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setStatus("ended");
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
            log.debug("[ConversationService] Ended conversation: id={}", conversationId);
        }
    }

    /**
     * 获取用户的活跃会话
     *
     * @param userId 用户ID
     * @return 会话，如果没有则返回 null
     */
    public Conversation getActiveConversation(String userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, "active")
                .orderByDesc(Conversation::getCreatedAt)
                .last("LIMIT 1");
        return conversationMapper.selectOne(wrapper);
    }
}
