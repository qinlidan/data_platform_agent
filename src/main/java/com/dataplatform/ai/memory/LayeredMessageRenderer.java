package com.dataplatform.ai.memory;

import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 三层消息渲染器
 * Layer 1: 原文层 - 保留最近几轮完整对话
 * Layer 2: 半压层 - 用户原文 + 助手截短 + 工具省略
 */
@Slf4j
@RequiredArgsConstructor
public class LayeredMessageRenderer {

    private final TokenBudgetCalculator calculator;

    /**
     * 渲染结果
     * @param messages 渲染后的消息列表
     * @param truncatedCount 被截断的消息数量
     * @param estimatedTokens 估算的总Token数
     */
    public record RenderedLayer(
        List<Message> messages,
        int truncatedCount,
        int estimatedTokens
    ) {}

    /**
     * Layer 1: 原文渲染（最近几轮完整对话）
     * 从最新消息向最旧遍历，直到预算用尽
     *
     * @param messages 原始消息列表
     * @param budget Token预算
     * @return 渲染结果
     */
    public RenderedLayer renderLayer1(List<Message> messages, int budget) {
        List<Message> rendered = new ArrayList<>();
        int totalTokens = 0;
        int truncatedCount = 0;

        // 从最新向最旧遍历，塞满预算
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            int tokens = calculator.estimateTokens(msg.getContent());

            if (totalTokens + tokens > budget) {
                truncatedCount = i + 1;
                break;
            }

            rendered.add(0, copyMessage(msg)); // 保持原序
            totalTokens += tokens;
        }

        log.debug("[Layer1] Rendered {} messages, truncated {}, tokens={}", rendered.size(), truncatedCount, totalTokens);
        return new RenderedLayer(rendered, truncatedCount, totalTokens);
    }

    /**
     * Layer 2: 半压渲染（用户原文 + 助手截短 + 工具省略）
     * 从最旧消息向最新遍历，应用压缩策略
     *
     * @param messages 原始消息列表
     * @param budget Token预算
     * @return 渲染结果
     */
    public RenderedLayer renderLayer2(List<Message> messages, int budget) {
        List<Message> rendered = new ArrayList<>();
        int totalTokens = 0;

        for (Message msg : messages) {
            Message compressed = compressMessage(msg);
            int tokens = calculator.estimateTokens(compressed.getContent());

            if (totalTokens + tokens > budget) {
                break;
            }

            rendered.add(compressed);
            totalTokens += tokens;
        }

        log.debug("[Layer2] Rendered {} messages, tokens={}", rendered.size(), totalTokens);
        return new RenderedLayer(rendered, 0, totalTokens);
    }

    /**
     * 压缩单条消息
     * - USER: 保留原文
     * - ASSISTANT: 截取前50字 + "..."
     * - TOOL: 省略为标识符
     *
     * @param original 原始消息
     * @return 压缩后的消息副本
     */
    private Message compressMessage(Message original) {
        Message compressed = copyMessage(original);

        if (original.getRole() == MessageRole.ASSISTANT) {
            // 助手消息：保留前 50 字
            String content = original.getContent();
            if (content != null && content.length() > 50) {
                // 按字符截断（非字节），确保 UTF-8 安全
                compressed.setContent(content.substring(0, 50) + "...");
            }
        } else if (original.getRole() == MessageRole.TOOL) {
            // 工具消息：省略为标识符
            compressed.setContent("[工具调用结果，已省略]");
        }
        // USER 消息保持原文

        return compressed;
    }

    /**
     * 复制消息对象
     * @param original 原始消息
     * @return 深拷贝副本
     */
    private Message copyMessage(Message original) {
        Message copy = new Message();
        copy.setId(original.getId());
        copy.setConversationId(original.getConversationId());
        copy.setRole(original.getRole());
        copy.setContent(original.getContent());
        copy.setToolCalls(original.getToolCalls());
        copy.setToolCallId(original.getToolCallId());
        copy.setCreatedAt(original.getCreatedAt());
        return copy;
    }
}
