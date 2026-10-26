package com.dataplatform.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;

/**
 * 可观察的工具回调包装器 - 发射工具调用事件到 UI
 * 使用代理模式，所有方法委托给原始 ToolCallback
 *
 * @author Data Platform Team
 */
@Slf4j
public class ObservableToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final Sinks.Many<Map<String, Object>> eventSink;

    public ObservableToolCallback(ToolCallback delegate, Sinks.Many<Map<String, Object>> eventSink) {
        this.delegate = delegate;
        this.eventSink = eventSink;
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
        // 获取工具名
        String toolName = getToolDefinition().name();
        String toolCallId = UUID.randomUUID().toString().substring(0, 8);

        // 发射 tool_start 事件
        emit("tool_start", Map.of(
                "toolName", toolName,
                "toolCallId", toolCallId,
                "arguments", functionArguments
        ));

        long startTime = System.currentTimeMillis();
        try {
            String result = delegate.call(functionArguments);
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

            throw e;
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 默认实现：忽略 ToolContext，调用单参数版本
        return call(toolInput);
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
