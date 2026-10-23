package com.dataplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具回调装饰器（Decorator Pattern）
 *
 * <p>在不修改原有工具类的前提下，为工具调用添加可观测性：
 * <ul>
 *   <li>工具调用开始/结束/失败事件</li>
 *   <li>执行耗时统计</li>
 *   <li>异常监控</li>
 * </ul>
 *
 * <p>设计模式：Decorator Pattern
 * <pre>
 * ToolCallback wrappedTool = new ObservableToolCallback(originalTool, eventSink);
 * wrappedTool.call(input); // 自动发送监控事件
 * </pre>
 *
 * @author Data Platform Team
 */
@Slf4j
public class ObservableToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final Sinks.Many<Map<String, Object>> uiEventSink;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造装饰器
     *
     * @param delegate 被装饰的原始工具回调
     * @param uiEventSink UI 事件通道（可为 null，此时仅记录日志）
     */
    public ObservableToolCallback(ToolCallback delegate, Sinks.Many<Map<String, Object>> uiEventSink) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate cannot be null");
        }
        this.delegate = delegate;
        this.uiEventSink = uiEventSink;
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
    public String call(String toolInput) {
        String toolName = getToolDefinition().name();
        log.info("[Tool Monitor] 开始调用工具: {}", toolName);
        emitToolStart(toolName, toolInput);

        long startTime = System.currentTimeMillis();
        try {
            String result = delegate.call(toolInput);
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Tool Monitor] 工具调用成功: {} (耗时: {}ms)", toolName, duration);
            emitToolEnd(toolName, result, duration, null);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Tool Monitor] 工具调用失败: {} (耗时: {}ms)", toolName, duration, e);
            emitToolEnd(toolName, null, duration, e.getMessage());
            throw e; // 重新抛出异常，保持原有行为
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        log.info("[Tool Monitor] 开始调用工具(带上下文): {}", toolName);
        emitToolStart(toolName, toolInput);

        long startTime = System.currentTimeMillis();
        try {
            String result = delegate.call(toolInput, toolContext);
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Tool Monitor] 工具调用成功(带上下文): {} (耗时: {}ms)", toolName, duration);
            emitToolEnd(toolName, result, duration, null);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Tool Monitor] 工具调用失败(带上下文): {} (耗时: {}ms)", toolName, duration, e);
            emitToolEnd(toolName, null, duration, e.getMessage());
            throw e; // 重新抛出异常，保持原有行为
        }
    }

    /**
     * 发送工具调用开始事件
     */
    private void emitToolStart(String toolName, String toolInput) {
        if (uiEventSink == null) {
            return; // 容错：如果没有 Sink，只记录日志
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "tool_start");
            Map<String, Object> payload = new HashMap<>();
            payload.put("toolName", toolName);
            payload.put("input", toolInput);
            payload.put("timestamp", System.currentTimeMillis());
            event.put("payload", payload);

            uiEventSink.tryEmitNext(event);
        } catch (Exception e) {
            log.warn("[Tool Monitor] 发送 tool_start 事件失败: {}", e.getMessage());
        }
    }

    /**
     * 发送工具调用结束事件
     *
     * @param errorMessage 如果调用失败，传入错误信息；成功则为 null
     */
    private void emitToolEnd(String toolName, String result, long durationMs, String errorMessage) {
        if (uiEventSink == null) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "tool_end");
            Map<String, Object> payload = new HashMap<>();
            payload.put("toolName", toolName);
            payload.put("durationMs", durationMs);
            payload.put("timestamp", System.currentTimeMillis());

            if (errorMessage != null) {
                payload.put("success", false);
                payload.put("error", errorMessage);
            } else {
                payload.put("success", true);
                // 不传输完整 result，避免过大（前端不需要显示）
            }

            event.put("payload", payload);

            uiEventSink.tryEmitNext(event);
        } catch (Exception e) {
            log.warn("[Tool Monitor] 发送 tool_end 事件失败: {}", e.getMessage());
        }
    }
}
