package com.dataplatform.ai.resolution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CoreferenceResolutionService {

    private final ChatClient resolutionChatClient;

    private static final String RESOLUTION_SYSTEM_PROMPT = """
            你是一个对话指代消解助手。

            任务：根据对话历史，把用户当前输入补全成一句不依赖上下文也能看懂的完整问句。

            规则：
            1. 如果用户消息包含指代词（刚才、那个、它、这个作业、那个数据源等），从历史中提取具体实体（ID、名称），补全到问句中。
               - 例：用户上一轮提到"作业ID为3"，这一轮说"刚那个作业再查一遍" → 改写为"查询作业ID为3的详情"
            2. 如果用户消息本身已完整、明确，原样返回，不要强行改写。
               - 例："查询作业ID为5的详情" → 原样返回
            3. 如果用户消息是口语化模糊表达（"再查一遍"、"帮我看看"），结合历史归一成标准问法。
               - 例：上一轮在查作业3，这一轮说"再查一遍" → "查询作业ID为3的详情"
            4. 只输出改写后的问句，不要解释、不要加前缀（如"改写为："），不要输出其他内容。

            示例：
            [历史] 用户: 查询作业ID为3的作业
            [历史] 助手: 作业ID为3，名称为xxx，状态为运行中...
            [当前] 用户: 刚那个作业再查一遍
            [输出] 查询作业ID为3的详情

            [当前] 用户: 查询作业ID为5的详情
            [输出] 查询作业ID为5的详情
            """;

    public CoreferenceResolutionService(ChatModel chatModel, ChatMemory chatMemory) {
        this.resolutionChatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String resolveAndRewrite(String userMessage, String conversationId) {
        // 空消息直接返回
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return userMessage;
        }

        try {
            log.info("开始指代消解: conversationId={}, 原始消息={}", conversationId, userMessage);

            String resolved = resolutionChatClient.prompt()
                    .system(RESOLUTION_SYSTEM_PROMPT)
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            // 降级策略：如果返回空，返回原始消息
            if (resolved == null || resolved.trim().isEmpty()) {
                log.warn("指代消解返回空，降级返回原始消息");
                return userMessage;
            }

            String result = resolved.trim();
            log.info("指代消解完成: 改写={}", result);
            return result;

        } catch (Exception e) {
            log.error("指代消解失败，降级返回原始消息: {}", e.getMessage());
            return userMessage;  // 降级策略
        }
    }
}
