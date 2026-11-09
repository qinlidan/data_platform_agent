package com.dataplatform.mapper;

import com.dataplatform.entity.Conversation;
import com.dataplatform.entity.ConversationStatus;
import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ConversationMapperTest {
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ConversationSummaryMapper summaryMapper;
    @Autowired
    private MessageMapper messageMapper;

    // 移除手动schema setup - 应该由Flyway/schema.sql处理
    // 测试应该在隔离的H2数据库中运行，不影响真实MySQL数据库

    @Test
    void testUpdateSummaryFields() {
        // 创建测试会话
        Conversation conv = new Conversation();
        conv.setUserId("test-user");
        conv.setTitle("test");
        conv.setStatus(ConversationStatus.ACTIVE);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);

        // 更新摘要字段
        int updated = conversationMapper.updateSummaryFields(conv.getId(), "test summary", 123L);
        assertEquals(1, updated);

        // 验证更新
        Conversation loaded = conversationMapper.selectById(conv.getId());
        assertEquals("test summary", loaded.getSummary());
        assertEquals(123L, loaded.getSummaryUptoMsgId());
    }

    @Test
    void testSelectByIdRange() {
        // 准备：插入测试会话
        Conversation conv = new Conversation();
        conv.setUserId("test-user-range");
        conv.setTitle("range test");
        conv.setStatus(ConversationStatus.ACTIVE);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        Long conversationId = conv.getId();

        // 插入 5 条消息，使用自增ID
        for (int i = 1; i <= 5; i++) {
            Message msg = new Message();
            msg.setConversationId(conversationId);
            msg.setRole(MessageRole.USER);
            msg.setContent("msg" + i);
            msg.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(msg);
        }

        // 获取插入的消息ID范围
        List<Message> allMessages = messageMapper.selectList(null);
        allMessages.sort((a, b) -> a.getId().compareTo(b.getId()));

        Long firstId = allMessages.get(0).getId();
        Long thirdId = allMessages.get(2).getId();
        Long fourthId = allMessages.get(3).getId();

        // 查询区间 (firstId, thirdId] 应返回 2条消息
        List<Message> result = messageMapper.selectByIdRange(conversationId, firstId, thirdId);
        assertEquals(2, result.size());
        assertEquals(allMessages.get(1).getId(), result.get(0).getId());
        assertEquals(thirdId, result.get(1).getId());
    }
}
