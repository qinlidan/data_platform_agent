package com.dataplatform.ai.graph;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 状态对象（兼容 Spring AI 原生类型）
 *
 * 技术栈支持：
 * - Spring AI（原生 Message 和 ToolCall）
 * - Spring AI Alibaba（Graph 能力）
 *
 * 包含：
 * - 消息历史（Spring AI Message）
 * - 工具调用（Spring AI ToolCall）
 * - 审批队列
 * - 元数据
 * - 控制标志
 */
@Data
public class AgentState {

    /**
     * 消息历史（使用 Spring AI 原生 Message 接口）
     * 兼容 UserMessage、AssistantMessage、ToolResponseMessage
     */
    private List<Message> messages = new ArrayList<>();

    /**
     * 待执行的工具调用（使用 Spring AI 标准 ToolCall）
     */
    private List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

    /**
     * 待审批操作（自定义结构）
     * 格式：{toolName: "deleteJob", params: {...}, reason: "删除不可逆"}
     */
    private List<Map<String, Object>> pendingApprovals = new ArrayList<>();

    /**
     * 元数据（userId, conversationId, sessionId 等）
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 中断标记（需要人工审批）
     */
    private boolean needsInterrupt = false;

    /**
     * 审批结果标记（供 Resume 恢复时读取）
     * "none" | "approved" | "rejected"
     */
    private String approvalStatus = "none";

    /**
     * 最终响应文本
     */
    private String finalResponse;

    // ========== 辅助方法 ==========

    /**
     * 添加消息（Spring AI 原生）
     */
    public void addMessage(Message message) {
        this.messages.add(message);
    }

    /**
     * 获取用户ID（从元数据）
     */
    public String getUserId() {
        return (String) metadata.getOrDefault("userId", "anonymous");
    }

    /**
     * 设置用户ID
     */
    public void setUserId(String userId) {
        metadata.put("userId", userId);
    }

    /**
     * 获取会话ID（从元数据）
     */
    public String getConversationId() {
        return (String) metadata.get("conversationId");
    }

    /**
     * 设置会话ID
     */
    public void setConversationId(String conversationId) {
        metadata.put("conversationId", conversationId);
    }

    /**
     * 是否已审批通过
     */
    public boolean isApproved() {
        return "approved".equals(approvalStatus);
    }

    /**
     * 是否已审批拒绝
     */
    public boolean isRejected() {
        return "rejected".equals(approvalStatus);
    }

    /**
     * 添加待审批操作
     */
    public void addPendingApproval(String toolName, Map<String, Object> params, String reason) {
        Map<String, Object> approval = new HashMap<>();
        approval.put("toolName", toolName);
        approval.put("params", params);
        approval.put("reason", reason);
        pendingApprovals.add(approval);
        needsInterrupt = true;
    }

    /**
     * 清除审批队列
     */
    public void clearPendingApprovals() {
        pendingApprovals.clear();
        needsInterrupt = false;
    }
}
