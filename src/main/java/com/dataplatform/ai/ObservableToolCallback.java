package com.dataplatform.ai;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Sinks;

/**
 * 包装 ToolCallback，在工具执行后把原始返回值旁路推送到 Sinks，
 * 用于流式对话中把结构化工具结果单独作为 SSE 事件推给前端，
 * 同时不影响工具结果正常返回给模型继续推理。
 */
public class ObservableToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final Sinks.Many<String> toolResultSink;

    public ObservableToolCallback(ToolCallback delegate, Sinks.Many<String> toolResultSink) {
        this.delegate = delegate;
        this.toolResultSink = toolResultSink;
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
        String result = delegate.call(toolInput);
        emit(result);
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        emit(result);
        return result;
    }

    private void emit(String result) {
        if (result != null && !result.isBlank()) {
            toolResultSink.tryEmitNext(result);
        }
    }
}
