package com.dataplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.IdGenerator;
import com.dataplatform.entity.Conversation;
import com.dataplatform.entity.ConversationStatus;
import com.dataplatform.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话服务
 */
@Service
@Slf4j
public class ConversationService extends ServiceImpl<ConversationMapper, Conversation> {

    private final IdGenerator idGenerator;

    public ConversationService(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 创建新会话
     *
     * @param userId 用户ID
     * @param title  会话标题
     * @return 会话ID
     */
    public Long createConversation(String userId, String title) {
        Long conversationId = idGenerator.nextId();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "新会话");
        conversation.setStatus(ConversationStatus.ACTIVE);

        save(conversation);
        log.debug("[ConversationService] Created new conversation: id={}, userId={}", conversationId, userId);

        return conversationId;
    }

    /**
     * 创建默认会话
     *
     * @param userId 用户ID
     * @return 会话ID
     */
    public Long createDefaultConversation(String userId) {
        return createConversation(userId, "新会话");
    }

    /**
     * 获取或创建会话
     * 如果 conversationId 为空，则创建新会话
     *
     * @param userId         用户ID
     * @param conversationId 会话ID（可为 null）
     * @return 会话ID
     */
    public Long getOrCreateConversation(String userId, Long conversationId) {
        if (conversationId == null) {
            return createDefaultConversation(userId);
        }

        // 验证会话是否存在且属于当前用户
        Conversation conversation = getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            log.warn("[ConversationService] Conversation not found or access denied: id={}, userId={}", conversationId, userId);
            return createDefaultConversation(userId);
        }

        return conversationId;
    }

    /**
     * 列出用户的活跃会话
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<Conversation> listByUserId(String userId) {
        return lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, ConversationStatus.ACTIVE)
                .isNull(Conversation::getDeletedAt)
                .orderByDesc(Conversation::getUpdatedAt)
                .list();
    }

    /**
     * 软删除会话
     *
     * @param conversationId 会话ID
     */
    public void softDelete(Long conversationId) {
        lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getStatus, ConversationStatus.DELETED)
                .set(Conversation::getDeletedAt, LocalDateTime.now())
                .update();
        log.debug("[ConversationService] Soft deleted conversation: id={}", conversationId);
    }

    /**
     * 更新会话标题
     *
     * @param conversationId 会话ID
     * @param title          新标题
     */
    public void updateTitle(Long conversationId, String title) {
        lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getTitle, title)
                .update();
        log.debug("[ConversationService] Updated conversation title: id={}, title={}", conversationId, title);
    }

    /**
     * 结束会话
     *
     * @param conversationId 会话ID
     */
    public void endConversation(Long conversationId) {
        lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getStatus, ConversationStatus.CLOSED)
                .update();
        log.debug("[ConversationService] Ended conversation: id={}", conversationId);
    }

    /**
     * 获取用户的活跃会话
     *
     * @param userId 用户ID
     * @return 会话，如果没有则返回 null
     */
    public Conversation getActiveConversation(String userId) {
        return lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, ConversationStatus.ACTIVE)
                .orderByDesc(Conversation::getCreatedAt)
                .last("LIMIT 1")
                .one();
    }
}
