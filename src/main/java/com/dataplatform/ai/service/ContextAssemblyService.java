package com.dataplatform.ai.service;

import com.dataplatform.ai.memory.LayeredMessageRenderer;
import com.dataplatform.ai.memory.TokenBudgetCalculator;
import com.dataplatform.entity.Conversation;
import com.dataplatform.entity.Message;
import com.dataplatform.mapper.ConversationMapper;
import com.dataplatform.mapper.ConversationSummaryMapper;
import com.dataplatform.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 三层上下文组装服务
 * 负责拼装 Layer 0（摘要层）+ Layer 2（半压层）+ Layer 1（原文层）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssemblyService {

    private final TokenBudgetCalculator budgetCalculator;
    private final LayeredMessageRenderer renderer;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final ContextSummaryService summaryService;

    /**
     * 组装三层上下文消息
     *
     * @param conversationId 会话ID
     * @return 组装后的消息列表（Spring AI Message格式）
     */
    public List<org.springframework.ai.chat.messages.Message> assembleThreeLayerContext(Long conversationId) {
        // 1. 加载会话元数据
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("[ContextAssembly] Conversation {} not found", conversationId);
            return new ArrayList<>();
        }

        // 2. 计算 Token 预算
        TokenBudgetCalculator.BudgetConfig budget = budgetCalculator.calculateBudget();

        // 3. 加载三层消息
        Long summaryUptoMsgId = conversation.getSummaryUptoMsgId();
        Long layer1FromMsgId = conversation.getLayer1FromMsgId();

        // Layer 0: 加载摘要
        String summaryText = conversation.getSummary();
        if (summaryText == null) {
            summaryText = "";
        }

        // Layer 2: 加载半压层消息 (summaryUptoMsgId, layer1FromMsgId]
        List<Message> layer2Messages = new ArrayList<>();
        if (summaryUptoMsgId != null && layer1FromMsgId != null && layer1FromMsgId > summaryUptoMsgId) {
            layer2Messages = messageMapper.selectByIdRange(conversationId, summaryUptoMsgId, layer1FromMsgId);
        }

        // Layer 1: 加载原文层消息 (id > layer1FromMsgId)
        List<Message> layer1Messages = new ArrayList<>();
        if (layer1FromMsgId != null) {
            layer1Messages = messageMapper.selectFromId(conversationId, layer1FromMsgId);
        } else {
            // 如果 layer1FromMsgId 未初始化，加载全部消息
            layer1Messages = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId)
                    .orderByAsc(Message::getId)
            );
        }

        // 4. 渲染三层消息
        LayeredMessageRenderer.RenderedLayer layer2Rendered = renderer.renderLayer2(layer2Messages, budget.layer2Budget());
        LayeredMessageRenderer.RenderedLayer layer1Rendered = renderer.renderLayer1(layer1Messages, budget.layer1Budget());

        // 5. 检查是否需要降级
        checkAndDegradeIfNeeded(conversation, layer1Rendered, layer2Rendered, budget);

        // 6. 拼装最终消息列表
        List<org.springframework.ai.chat.messages.Message> finalMessages = new ArrayList<>();

        // Layer 0 摘要作为系统消息（如果有）
        if (!summaryText.isEmpty()) {
            SystemMessage summaryMessage = new SystemMessage(
                "【会话摘要】\n" + summaryText + "\n\n以上是之前对话的摘要，供你参考历史上下文。"
            );
            finalMessages.add(summaryMessage);
        }

        // Layer 2 半压消息
        for (Message msg : layer2Rendered.messages()) {
            finalMessages.add(convertToAIMessage(msg));
        }

        // Layer 1 原文消息
        for (Message msg : layer1Rendered.messages()) {
            finalMessages.add(convertToAIMessage(msg));
        }

        // 7. 打印 history_ctx 日志
        int totalEstimatedTokens = layer2Rendered.estimatedTokens() + layer1Rendered.estimatedTokens();
        log.info("[history_ctx] conversationId={} summaryLines={} messageCount={} " +
                "layer1={} layer2={} totalTokens={}",
                conversationId,
                summaryText.isEmpty() ? 0 : summaryText.split("\n\n").length,
                finalMessages.size(),
                layer1Rendered.messages().size(),
                layer2Rendered.messages().size(),
                totalEstimatedTokens
        );

        return finalMessages;
    }

    /**
     * 检查并执行降级（如果需要）
     */
    private void checkAndDegradeIfNeeded(
            Conversation conversation,
            LayeredMessageRenderer.RenderedLayer layer1Rendered,
            LayeredMessageRenderer.RenderedLayer layer2Rendered,
            TokenBudgetCalculator.BudgetConfig budget
    ) {
        // Layer 1 超预算 → 降级到 Layer 2
        if (layer1Rendered.truncatedCount() > 0) {
            log.warn("[layer1 degrade] Conversation {} layer1 truncated {} messages",
                    conversation.getId(), layer1Rendered.truncatedCount());

            // 计算新的 layer1_from_msg_id（向后移动被截断的消息数量）
            List<Message> allLayer1 = messageMapper.selectFromId(
                conversation.getId(),
                conversation.getLayer1FromMsgId()
            );

            if (allLayer1.size() > layer1Rendered.truncatedCount()) {
                // 取被截断后的第一条消息 ID
                Long newLayer1From = allLayer1.get(layer1Rendered.truncatedCount()).getId();

                // 更新锚点
                conversation.setLayer1FromMsgId(newLayer1From);
                conversationMapper.updateById(conversation);

                log.info("[layer1 degrade] Moved layer1_from {} → {}",
                    conversation.getLayer1FromMsgId(), newLayer1From);
            }
        }

        // Layer 2 超预算 → 异步触发摘要生成
        if (layer2Rendered.estimatedTokens() > budget.layer2Budget()) {
            log.warn("[summary trigger] Conversation {} layer2 ≈{} tokens > budget {}",
                    conversation.getId(), layer2Rendered.estimatedTokens(), budget.layer2Budget());

            // 触发异步摘要任务
            Long fromMsgId = conversation.getSummaryUptoMsgId() != null
                    ? conversation.getSummaryUptoMsgId()
                    : 0L;
            Long uptoMsgId = conversation.getLayer1FromMsgId();

            if (uptoMsgId != null && uptoMsgId > fromMsgId) {
                summaryService.summarizeLayer2Async(conversation.getId(), fromMsgId, uptoMsgId);
            }
        }
    }

    /**
     * 将数据库实体转换为 Spring AI Message
     */
    private org.springframework.ai.chat.messages.Message convertToAIMessage(Message entity) {
        return switch (entity.getRole()) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
            case TOOL -> new org.springframework.ai.chat.messages.ToolResponseMessage(
                    List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                            entity.getToolCallId() != null ? entity.getToolCallId() : "unknown",
                            "tool",  // tool name
                            entity.getContent() != null ? entity.getContent() : ""
                    ))
            );
        };
    }
}
