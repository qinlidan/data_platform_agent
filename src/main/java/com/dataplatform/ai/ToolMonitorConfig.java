package com.dataplatform.ai;

import com.dataplatform.ai.executor.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * 工具监控配置类
 * <p>
 * 提供工具包装的工厂方法，为工具添加监控和治理能力：
 * <ul>
 *   <li>UI 事件发射（tool_start, tool_end）</li>
 *   <li>拦截器链执行（审计、权限、确认、校验、超时重试）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * ToolCallback[] monitored = ToolMonitorConfig.wrapTools(
 *     eventSink,
 *     toolExecutor,
 *     jobTool,
 *     dataSourceTool
 * );
 * </pre>
 *
 * @author Data Platform Team
 */
@Slf4j
public class ToolMonitorConfig {

    /**
     * 包装工具列表，为每个工具添加监控和治理能力
     *
     * @param eventSink    UI 事件通道（可为 null）
     * @param toolExecutor 工具执行器（必需，提供拦截器链）
     * @param tools        原始工具对象（带 @Tool 注解的类）
     * @return 包装后的 ToolCallback 数组，可直接传给 ChatClient
     */
    public static ToolCallback[] wrapTools(
            Sinks.Many<Map<String, Object>> eventSink,
            ToolExecutor toolExecutor,
            Object... tools
    ) {
        if (tools == null || tools.length == 0) {
            log.warn("[Tool Monitor] 没有工具需要包装");
            return new ToolCallback[0];
        }

        if (toolExecutor == null) {
            throw new IllegalArgumentException("ToolExecutor 不能为 null");
        }

        // 统计总共有多少个工具方法
        int totalCallbacks = 0;
        for (Object tool : tools) {
            ToolCallback[] callbacks = ToolCallbacks.from(tool);
            totalCallbacks += callbacks.length;
        }

        // 创建包装后的数组
        ToolCallback[] wrapped = new ToolCallback[totalCallbacks];
        int index = 0;

        for (Object tool : tools) {
            ToolCallback[] callbacks = ToolCallbacks.from(tool);
            for (ToolCallback callback : callbacks) {
                wrapped[index++] = new ObservableToolCallback(callback, eventSink, toolExecutor);
            }
        }

        log.info("[Tool Monitor] 已包装 {} 个工具，共 {} 个方法", tools.length, totalCallbacks);
        return wrapped;
    }

    /**
     * 包装单个工具
     */
    public static ToolCallback[] wrapTool(
            Sinks.Many<Map<String, Object>> eventSink,
            ToolExecutor toolExecutor,
            Object tool
    ) {
        return wrapTools(eventSink, toolExecutor, tool);
    }
}
