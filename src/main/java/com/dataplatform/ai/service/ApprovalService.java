package com.dataplatform.ai.service;

import com.dataplatform.ai.checkpoint.RedisCheckpoint;
import com.dataplatform.ai.graph.AgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 人工审批服务
 *
 * 职责：
 * 1. 保存需要审批的状态到 Checkpoint
 * 2. 等待用户审批（approved / rejected）
 * 3. 恢复状态并继续执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final RedisCheckpoint checkpoint;
    private final ObjectMapper objectMapper;

    /**
     * 保存审批 Checkpoint
     *
     * @param state 当前状态
     * @return Checkpoint ID
     */
    public Mono<String> saveApprovalCheckpoint(AgentState state) {
        try {
            // 生成 Checkpoint ID
            String checkpointId = generateCheckpointId(state);

            log.info("[ApprovalService] 保存审批 Checkpoint：{}", checkpointId);

            // 保存状态
            checkpoint.save(checkpointId, state);

            return Mono.just(checkpointId);

        } catch (Exception e) {
            log.error("[ApprovalService] 保存 Checkpoint 失败", e);
            return Mono.error(e);
        }
    }

    /**
     * 处理用户审批
     *
     * @param checkpointId Checkpoint ID
     * @param approved     是否批准
     * @return 恢复的状态
     */
    public Mono<AgentState> handleApproval(String checkpointId, boolean approved) {
        try {
            log.info("[ApprovalService] 处理审批：checkpointId={}, approved={}", checkpointId, approved);

            // 加载状态
            AgentState state = checkpoint.load(checkpointId);

            if (state == null) {
                log.error("[ApprovalService] Checkpoint 不存在或已过期：{}", checkpointId);
                return Mono.error(new RuntimeException("Checkpoint 不存在或已过期"));
            }

            // 更新审批状态
            state.setApprovalStatus(approved ? "approved" : "rejected");

            if (approved) {
                // 审批通过：将待审批操作转换为工具调用
                List<AssistantMessage.ToolCall> toolCalls = convertPendingApprovalsToToolCalls(state);
                state.setToolCalls(toolCalls);

                log.info("[ApprovalService] 审批通过，生成 {} 个工具调用", toolCalls.size());
            } else {
                log.info("[ApprovalService] 审批拒绝");
            }

            // 清空待审批队列
            state.clearPendingApprovals();

            // 删除 Checkpoint（已使用）
            checkpoint.delete(checkpointId);

            return Mono.just(state);

        } catch (Exception e) {
            log.error("[ApprovalService] 处理审批失败", e);
            return Mono.error(e);
        }
    }

    /**
     * 将待审批操作转换为 Spring AI ToolCall
     */
    private List<AssistantMessage.ToolCall> convertPendingApprovalsToToolCalls(AgentState state) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        for (Map<String, Object> approval : state.getPendingApprovals()) {
            String toolName = (String) approval.get("toolName");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) approval.get("params");

            try {
                // 将参数转换为 JSON 字符串
                String arguments = objectMapper.writeValueAsString(params);

                // 生成 ToolCall ID
                String toolCallId = "call_" + System.currentTimeMillis();

                // 创建 ToolCall（使用 Spring AI 原生类型）
                AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                        toolCallId,
                        "function",
                        toolName,
                        arguments
                );

                toolCalls.add(toolCall);

            } catch (Exception e) {
                log.error("[ApprovalService] 转换工具调用失败：{}", toolName, e);
            }
        }

        return toolCalls;
    }

    /**
     * 生成 Checkpoint ID
     */
    private String generateCheckpointId(AgentState state) {
        String userId = state.getUserId();
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("approval:%s:%s", userId, timestamp);
    }

    /**
     * 检查 Checkpoint 是否存在
     */
    public Mono<Boolean> checkpointExists(String checkpointId) {
        try {
            AgentState state = checkpoint.load(checkpointId);
            return Mono.just(state != null);
        } catch (Exception e) {
            log.error("[ApprovalService] 检查 Checkpoint 失败", e);
            return Mono.just(false);
        }
    }
}
