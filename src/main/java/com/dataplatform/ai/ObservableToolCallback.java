package com.dataplatform.ai;

import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.executor.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * 可观察的工具回调包装器
 * <p>
 * 集成拦截器链，提供完整的工具治理能力：
 * <ul>
 *   <li>发射工具调用事件到前端（tool_start, tool_end）</li>
 *   <li>通过拦截器链执行：审计、权限、确认、校验、超时重试</li>
 * </ul>
 *
 * @author Data Platform Team
 */
@Slf4j
public class ObservableToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final Sinks.Many<Map<String, Object>> eventSink;
    private final ToolExecutor toolExecutor;

    public ObservableToolCallback(
            ToolCallback delegate,
            Sinks.Many<Map<String, Object>> eventSink,
            ToolExecutor toolExecutor) {
        this.delegate = delegate;
        this.eventSink = eventSink;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String functionArguments) {
        // 无 ToolContext 的调用，使用匿名用户
        return call(functionArguments, new ToolContext(Collections.emptyMap()));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 优先使用带 ToolContext 的重载版本
        String toolName = getToolDefinition().name();
        String toolCallId = UUID.randomUUID().toString().substring(0, 8);

        // 发射 tool_start 事件
        emit("tool_start", Map.of(
                "toolName", toolName,
                "toolCallId", toolCallId,
                "arguments", toolInput
        ));

        long startTime = System.currentTimeMillis();

        try {
            // 从 ToolContext 提取用户信息并构建执行上下文
            ToolExecutionContext context = buildContext(toolName, toolInput, toolContext);

            // 通过拦截器链执行工具调用（不再使用 ThreadLocal）
            String result = toolExecutor.execute(context, ctx -> delegate.call(toolInput, toolContext));

            long duration = System.currentTimeMillis() - startTime;

            // 发射 tool_end 事件（成功）
            emit("tool_end", Map.of(
                    "toolName", toolName,
                    "toolCallId", toolCallId,
                    "success", true,
                    "result", result,
                    "duration", duration
            ));

            return result;

        } catch (com.dataplatform.ai.exception.NeedConfirmationException e) {
            // 需要二次确认的异常，通过 SSE 事件流返回前端
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[ObservableToolCallback] 工具 {} 需要二次确认: token={}", toolName, e.getConfirmationToken());

            // 发射 confirmation_required 事件（包含 token）
            emit("confirmation_required", Map.of(
                    "toolName", toolName,
                    "toolCallId", toolCallId,
                    "confirmationToken", e.getConfirmationToken(),
                    "message", e.getMessage(),
                    "duration", duration
            ));

            // 返回提示信息给 AI 模型
            return "该操作需要用户二次确认。已向用户界面发送确认按钮，等待用户点击按钮确认。";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ObservableToolCallback] 工具 {} 执行失败", toolName, e);

            // 发射 tool_end 事件（失败）
            emit("tool_end", Map.of(
                    "toolName", toolName,
                    "toolCallId", toolCallId,
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    "duration", duration
            ));

            // 返回错误信息给模型
            return "执行失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * 构建工具执行上下文（从 ToolContext 提取用户信息）
     */
    private ToolExecutionContext buildContext(String toolName, String arguments, ToolContext toolContext) {
        Map<String, Object> contextMap = toolContext != null ? toolContext.getContext() : Map.of();

        // 从 ToolContext 提取用户信息（前端注入，AI 无法伪造）
        String userId = (String) contextMap.getOrDefault("userId", "anonymous");
        String conversationId = (String) contextMap.getOrDefault("conversationId", UUID.randomUUID().toString());
        String confirmationToken = (String) contextMap.get("confirmationToken");  // 提取确认令牌

        @SuppressWarnings("unchecked")
        java.util.Set<String> roles = (java.util.Set<String>) contextMap.getOrDefault("roles",
                java.util.Set.of("USER"));

        return ToolExecutionContext.builder()
                .toolName(toolName)
                .toolInput(arguments)
                .conversationId(conversationId)
                .userId(userId)
                .roles(roles)
                .confirmationToken(confirmationToken)  // 设置确认令牌
                .build();
    }

    /**
     * 发射事件到 UI（非阻塞）
     */
    private void emit(String event, Object payload) {
        if (eventSink != null) {
            Sinks.EmitResult result = eventSink.tryEmitNext(Map.of("event", event, "payload", payload));
            if (result.isFailure()) {
                log.warn("[ObservableToolCallback] UI 事件发射失败: event={}, result={}", event, result);
            }
        }
    }
}
