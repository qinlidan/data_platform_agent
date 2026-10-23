package com.dataplatform.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置
 * 用于多轮对话上下文管理
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 配置 ChatMemory Bean
     * 使用内存存储 + 窗口裁剪策略（保留最近 20 条消息）
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)  // 保留最近 20 条消息（约 10 轮对话）
                .build();
    }
}
