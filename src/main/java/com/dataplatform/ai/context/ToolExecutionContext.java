package com.dataplatform.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具执行上下文
 *
 * <p>在拦截器链中传递的上下文对象，包含工具调用所需的全部信息：
 * - 工具定义和参数
 * - 用户身份和权限
 * - 会话信息
 * - 确认令牌
 * - 扩展属性
 *
 * <p>设计原则：
 * - 不可变性：各拦截器应该只读取上下文，不应修改（除非有充分理由）
 * - 完整性：包含所有拦截器可能需要的信息，避免从外部重新获取
 * - 可扩展：通过 attributes 支持自定义属性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具调用参数（JSON 字符串）
     */
    private String toolInput;

    /**
     * 工具定义（包含 name, description, inputSchema 等）
     */
    private Map<String, Object> toolDefinition;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 用户角色集合
     */
    private Set<String> roles;

    /**
     * 会话 ID（用于追踪对话上下文）
     */
    private String conversationId;

    /**
     * 二次确认令牌（用户已确认时由前端传入）
     */
    private String confirmationToken;

    /**
     * 扩展属性（用于拦截器间传递额外信息）
     * 例如：
     * - "startTime": 记录开始时间，用于计算耗时
     * - "retryCount": 记录重试次数
     * - "validationResult": 校验结果详情
     */
    @Builder.Default
    private Map<String, Object> attributes = new java.util.HashMap<>();

    /**
     * 设置扩展属性
     */
    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * 获取扩展属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) this.attributes.get(key);
    }

    /**
     * 获取扩展属性（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = this.attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 是否包含某个扩展属性
     */
    public boolean hasAttribute(String key) {
        return this.attributes.containsKey(key);
    }
}
