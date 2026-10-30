package com.dataplatform.ai.resolution;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Slf4j
class CoreferenceResolutionServiceTest {

    @Autowired
    private CoreferenceResolutionService service;

    @Autowired
    private ChatMemory chatMemory;

    private String testConversationId;

    @BeforeEach
    void setUp() {
        testConversationId = "test-" + System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        // 清理测试会话历史
        chatMemory.clear(testConversationId);
    }

    @Test
    void 用例1_指代消解_作业ID() {
        // 模拟历史：先查询作业3
        chatMemory.add(testConversationId, new UserMessage("查询作业ID为3的作业"));
        chatMemory.add(testConversationId, new AssistantMessage("作业ID为3，名称为xxx..."));

        // 当前消息：带指代
        String result = service.resolveAndRewrite("刚那个作业再查一遍", testConversationId);

        // 断言：应补全为完整问句（包含作业ID 3）
        assertThat(result).containsIgnoringCase("作业");
        assertThat(result).containsIgnoringCase("3");
        log.info("用例1结果: {}", result);
    }

    @Test
    void 用例2_原样透传_无指代() {
        String input = "查询作业ID为5的详情";
        String result = service.resolveAndRewrite(input, testConversationId);

        // 断言：应原样返回或语义等价
        assertThat(result).containsIgnoringCase("作业");
        assertThat(result).containsIgnoringCase("5");
        log.info("用例2结果: {}", result);
    }

    @Test
    void 用例3_降级策略_空消息() {
        String result = service.resolveAndRewrite("", testConversationId);
        assertEquals("", result);
    }

    @Test
    void 用例4_降级策略_无历史() {
        // 新会话，无历史
        String result = service.resolveAndRewrite("查询作业详情", testConversationId);

        // 断言：无历史时应原样返回
        assertThat(result).isNotEmpty();
        log.info("用例4结果: {}", result);
    }
}
