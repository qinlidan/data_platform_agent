package com.dataplatform.ai.service;

import com.dataplatform.entity.Conversation;
import com.dataplatform.entity.ConversationSummary;
import com.dataplatform.entity.Message;
import com.dataplatform.entity.MessageRole;
import com.dataplatform.mapper.ConversationMapper;
import com.dataplatform.mapper.ConversationSummaryMapper;
import com.dataplatform.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上下文摘要服务
 * 负责生成 Layer 0 摘要和触发摘要更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextSummaryService {

    private final ChatClient.Builder chatClientBuilder;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationSummaryMapper summaryMapper;

    /**
     * 异步生成 Layer 2 摘要
     * 当 Layer 2 超预算时触发，将 [summary_upto_msg_id, layer1_from_msg_id] 的消息压缩到 Layer 0
     *
     * @param conversationId 会话ID
     * @param fromMsgId      起始消息ID（不含）
     * @param uptoMsgId      结束消息ID（含）
     */
    @Async
    public void summarizeLayer2Async(Long conversationId, Long fromMsgId, Long uptoMsgId) {
        try {
            log.info("[Summary] Start summarizing conversation {} from {} to {}",
                    conversationId, fromMsgId, uptoMsgId);

            // 1. 加载需要摘要的消息
            List<Message> messages = messageMapper.selectByIdRange(conversationId, fromMsgId, uptoMsgId);
            if (messages.isEmpty()) {
                log.warn("[Summary] No messages to summarize");
                return;
            }

            // 2. 构造摘要提示词
            String summaryPrompt = buildSummaryPrompt(messages);

            // 3. 调用 LLM 生成摘要
            ChatClient chatClient = chatClientBuilder.build();
            String newSummary = chatClient.prompt()
                    .user(summaryPrompt)
                    .call()
                    .content();

            // 4. 加载会话和计算新的 seq
            Conversation conversation = conversationMapper.selectById(conversationId);
            List<ConversationSummary> existingSummaries = summaryMapper.selectByConversationId(conversationId);
            int nextSeq = existingSummaries.isEmpty() ? 1 : existingSummaries.get(existingSummaries.size() - 1).getSeq() + 1;

            // 5. 保存新摘要段落到 conversation_summaries 表
            ConversationSummary summarySegment = new ConversationSummary();
            summarySegment.setConversationId(conversationId);
            summarySegment.setSeq(nextSeq);
            summarySegment.setFromMsgId(fromMsgId);
            summarySegment.setUptoMsgId(uptoMsgId);
            summarySegment.setContent(newSummary);
            summaryMapper.insert(summarySegment);

            log.info("[Summary] Saved summary segment {} for conversation {}, msg range ({}, {}]",
                    nextSeq, conversationId, fromMsgId, uptoMsgId);

            // 6. 拼接所有摘要段落并更新 conversations.summary 字段
            String fullSummary = existingSummaries.stream()
                    .map(ConversationSummary::getContent)
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n\n" + b);
            fullSummary = fullSummary.isEmpty() ? newSummary : fullSummary + "\n\n" + newSummary;

            // 7. 更新会话摘要和锚点
            conversation.setSummary(fullSummary);
            conversation.setSummaryUptoMsgId(uptoMsgId);
            conversationMapper.updateById(conversation);

            log.info("[Summary] Completed summarizing conversation {}, total summary length: {}",
                    conversationId, fullSummary.length());

        } catch (Exception e) {
            log.error("[Summary] Failed to summarize conversation " + conversationId, e);
        }
    }

    /**
     * 构造摘要提示词
     */
    private String buildSummaryPrompt(List<Message> messages) {
        StringBuilder dialogue = new StringBuilder();

        for (Message msg : messages) {
            String roleLabel = switch (msg.getRole()) {
                case USER -> "用户";
                case ASSISTANT -> "助手";
                case SYSTEM -> "系统";
                case TOOL -> "工具";
            };

            dialogue.append(String.format("[%s] %s\n", roleLabel, msg.getContent()));
        }

        return String.format("""
                你是专业的对话摘要助手。提炼以下对话中的事实、诉求和关键信息：
                - 明确记录：项目编号、数据集名、表名、明确诉求
                - 保留技术细节：SQL、API、字段名、配置参数
                - 排除：寒暄、确认回复、中间过程
                - 使用客观第三人称，按时间顺序，每段独立

                【对话记录】
                %s

                请输出摘要：
                """, dialogue);
    }
}
