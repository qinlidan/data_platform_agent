package com.dataplatform.mapper;

import com.dataplatform.entity.LowConfidenceQuestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实 MySQL 验证：低置信度池能写入并读回。
 * Mockito 测试只能证明 RAGService 调了 insert，证明不了表名映射、字段映射、ENUM 取值是对的。
 * conversationId 用 null：表上若带外键约束，伪造的会话 id 会被拒绝，这里只验证映射本身。
 */
@SpringBootTest
class LowConfidenceQuestionMapperIT {

    @Autowired
    private LowConfidenceQuestionMapper mapper;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanup() {
        created.forEach(mapper::deleteById);
    }

    private LowConfidenceQuestion insert(String source, String reason) {
        LowConfidenceQuestion q = LowConfidenceQuestion.builder()
            .rawQuestion("IT-明天天气怎么样？")
            .source(source)
            .reason(reason)
            .build();
        mapper.insert(q);
        assertNotNull(q.getId(), "自增主键应回填");
        created.add(q.getId());
        return q;
    }

    @Test
    void insertsAndReadsBackAllFields() {
        LowConfidenceQuestion saved = insert("retrieval_low_conf", "向量相似度过低: 0.3000 < 阈值 0.60");

        LowConfidenceQuestion loaded = mapper.selectById(saved.getId());

        assertEquals("IT-明天天气怎么样？", loaded.getRawQuestion());
        assertEquals("retrieval_low_conf", loaded.getSource());
        assertEquals("向量相似度过低: 0.3000 < 阈值 0.60", loaded.getReason());
        assertNotNull(loaded.getCreatedAt(), "created_at 由数据库默认值生成");
    }

    @Test
    void acceptsAllThreeSourceEnumValues() {
        for (String source : new String[]{"retrieval_low_conf", "self_check", "user_feedback"}) {
            LowConfidenceQuestion saved = insert(source, "r");
            assertEquals(source, mapper.selectById(saved.getId()).getSource());
        }
    }

    @Test
    void reasonLongerThanColumnLimitIsHandledByCallerNotSilentlyTruncated() {
        // RAGService 在写入前把 reason 截到 500 字符；这里验证 500 字符恰好能写入
        LowConfidenceQuestion saved = insert("self_check", "x".repeat(500));
        assertEquals(500, mapper.selectById(saved.getId()).getReason().length());
    }
}
