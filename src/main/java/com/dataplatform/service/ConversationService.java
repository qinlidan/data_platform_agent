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
        log.info("[ConversationService] 准备创建会话: conversationId={}, userId={}, title={}", conversationId, userId, title);

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "新会话");
        conversation.setStatus(ConversationStatus.ACTIVE);

        // 兜底：如果自动填充失败，手动设置时间
        if (conversation.getCreatedAt() == null) {
            conversation.setCreatedAt(LocalDateTime.now());
        }
        if (conversation.getUpdatedAt() == null) {
            conversation.setUpdatedAt(LocalDateTime.now());
        }

        log.info("[ConversationService] 调用 save() 保存会话到数据库: {}", conversation);
        boolean saved = save(conversation);
        log.info("[ConversationService] save() 返回结果: saved={}", saved);

        if (!saved) {
            log.error("[ConversationService] ❌ Failed to create conversation: userId={}, title={}", userId, title);
            throw new RuntimeException("创建会话失败");
        }

        // 验证是否真的保存成功
        Conversation verify = getById(conversationId);
        if (verify == null) {
            log.error("[ConversationService] ❌ 严重错误：save() 返回 true 但数据库中查不到记录: conversationId={}", conversationId);
        } else {
            log.info("[ConversationService] ✅ 验证成功：会话已保存到数据库: conversationId={}", conversationId);
        }

        log.info("[ConversationService] ✅ Created new conversation: id={}, userId={}", conversationId, userId);

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
