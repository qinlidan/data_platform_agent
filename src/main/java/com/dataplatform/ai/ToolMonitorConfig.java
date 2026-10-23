package com.dataplatform.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * 工具监控配置类
 *
 * <p>提供工具包装的工厂方法，简化装饰器的使用：
 * <pre>
 * ToolCallback[] monitored = ToolMonitorConfig.wrapTools(eventSink, jobTool, dsTool);
 * </pre>
 *
 * @author Data Platform Team
 */
@Slf4j
public class ToolMonitorConfig {

    /**
     * 包装工具列表，为每个工具添加监控能力
     *
     * @param eventSink UI 事件通道（可为 null）
     * @param tools 原始工具对象（带 @Tool 注解的类）
     * @return 包装后的 ToolCallback 数组，可直接传给 ChatClient
     */
    public static ToolCallback[] wrapTools(Sinks.Many<Map<String, Object>> eventSink, Object... tools) {
        if (tools == null || tools.length == 0) {
            log.warn("[Tool Monitor] 没有工具需要包装");
            return new ToolCallback[0];
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
                wrapped[index++] = new ObservableToolCallback(callback, eventSink);
            }
        }

        log.info("[Tool Monitor] 已包装 {} 个工具，共 {} 个方法", tools.length, totalCallbacks);
        return wrapped;
    }

    /**
     * 包装单个工具
     */
    public static ToolCallback[] wrapTool(Sinks.Many<Map<String, Object>> eventSink, Object tool) {
        return wrapTools(eventSink, tool);
    }
}