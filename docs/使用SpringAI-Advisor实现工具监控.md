# 使用 Spring AI Advisor 实现工具监控

## 问题

当前使用**自定义装饰器模式**（`ObservableToolCallback`）实现工具监控，但 **Spring AI 框架自带 Advisor 机制**，更原生、更优雅。

---

## Spring AI Advisor 机制

### 1. Advisor 是什么

Spring AI 的 `Advisor` 是一个**拦截器链**，可以在 AI 调用的不同阶段插入逻辑：
- **Before Request** - 请求发送前（修改 Prompt、添加工具、设置参数）
- **After Request** - 请求完成后（记录日志、收集指标、修改响应）
- **Around Tool Call** - 工具调用前后（监控、重试、限流）

### 2. 已有 Advisor 示例

项目中已使用 `MessageChatMemoryAdvisor` 实现多轮对话：

```java
// AiAssistantController.java 第 240-242 行
ChatClient client = ChatClient.builder(chatModel)
    .defaultSystem(pureConversationSystemPrompt.getTemplate())
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()  // ✅ 已使用 Advisor
    )
    .build();
```

### 3. Spring AI 内置 Advisor

Spring AI 1.0.3 提供以下内置 Advisor：

| Advisor | 功能 |
|---------|------|
| `MessageChatMemoryAdvisor` | 多轮对话上下文管理 |
| `PromptChatMemoryAdvisor` | Prompt 级别的上下文管理 |
| `QuestionAnswerAdvisor` | RAG 检索增强 |
| `VectorStoreChatMemoryAdvisor` | 向量数据库记忆 |
| **自定义 Advisor** | 实现 `Advisor` 接口 |

**但是**：Spring AI 1.0.3 **没有内置的工具监控 Advisor**，需要自定义实现。

---

## 方案对比

### 方案 1：当前装饰器模式（已实现）

```java
// 工具监控配置
public class ToolMonitorConfig {
    public static ToolCallback[] wrapTools(
            Sinks.Many<Map<String, Object>> eventSink, 
            Object... tools) {
        // 装饰器包装
    }
}

// 使用
ToolCallback[] monitoredTools = ToolMonitorConfig.wrapTools(uiEventSink, jobTool, dataSourceTool);
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultToolCallbacks(monitoredTools)
    .build();
```

**优点**：
✅ 简单直接，容易理解  
✅ 对 Spring AI 版本无依赖  

**缺点**：
❌ 需要手动包装 `wrapTools()`  
❌ 不是 Spring AI 的标准做法  
❌ 无法监控 Advisor 链中的其他事件  

---

### 方案 2：自定义 Spring AI Advisor（推荐）

```java
/**
 * 工具监控 Advisor - 拦截工具调用并推送事件到前端
 */
@Slf4j
public class ToolMonitorAdvisor implements Advisor {
    
    private final Sinks.Many<Map<String, Object>> uiEventSink;
    
    public ToolMonitorAdvisor(Sinks.Many<Map<String, Object>> uiEventSink) {
        this.uiEventSink = uiEventSink;
    }
    
    @Override
    public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
        // 请求前：记录工具调用开始
        log.info("[Tool Monitor Advisor] 开始请求，可用工具：{}", 
                 request.tools() != null ? request.tools().size() : 0);
        return request;
    }
    
    @Override
    public AdvisedResponse adviseResponse(AdvisedResponse response, Map<String, Object> context) {
        // 响应后：解析工具调用结果
        if (response.result() != null && response.result().getMetadata() != null) {
            var metadata = response.result().getMetadata();
            
            // 检查是否有工具调用
            Object toolCalls = metadata.get("tool_calls");
            if (toolCalls != null) {
                log.info("[Tool Monitor Advisor] 检测到工具调用：{}", toolCalls);
                emitToolEvent(toolCalls);
            }
        }
        return response;
    }
    
    private void emitToolEvent(Object toolCalls) {
        if (uiEventSink == null) return;
        
        Map<String, Object> event = new HashMap<>();
        event.put("event", "tool_call");
        event.put("payload", Map.of(
            "toolCalls", toolCalls,
            "timestamp", System.currentTimeMillis()
        ));
        uiEventSink.tryEmitNext(event);
    }
}
```

**使用方式**：

```java
@GetMapping("/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    
    // 创建 UI 事件通道
    Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();
    
    // 构建工具
    JobTool jobTool = new JobTool(jobService, dataSourceService);
    jobTool.setUiEventSink(uiEventSink);  // 工具内部表格推送
    DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);
    
    // ✅ 使用 Advisor（不再需要 wrapTools）
    ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(jobTool, dataSourceTool)  // 直接传工具
        .defaultAdvisors(
            new ToolMonitorAdvisor(uiEventSink)  // 添加监控 Advisor
        )
        .build();
    
    // 流式调用
    Flux<ServerSentEvent<Map<String, Object>>> textStream = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(message)
        .stream()
        .chatResponse()
        .mapNotNull(resp -> { /* ... */ });
    
    // 合并文本流和 UI 事件流
    Flux<ServerSentEvent<Map<String, Object>>> uiStream = uiEventSink.asFlux()
        .map(event -> { /* ... */ });
    
    return Flux.merge(textStream, uiStream)
        .concatWith(/* done event */);
}
```

**优点**：
✅ **零样板代码** - 不需要 `wrapTools()`  
✅ **Spring AI 原生** - 符合框架设计  
✅ **功能更强** - 可以监控整个请求/响应生命周期  
✅ **易组合** - 可以和其他 Advisor 链式组合  
✅ **未来兼容** - Spring AI 官方可能会推出更多 Advisor  

**潜在问题**：
⚠️ **元数据格式** - 需要验证 `metadata.get("tool_calls")` 的实际格式  
⚠️ **工具内部推送** - 工具方法内部的表格推送仍需 `uiEventSink`  

---

## 实现步骤

### 第 1 步：创建 `ToolMonitorAdvisor`

```java
package com.dataplatform.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ToolMonitorAdvisor implements Advisor {
    
    private final Sinks.Many<Map<String, Object>> uiEventSink;
    
    public ToolMonitorAdvisor(Sinks.Many<Map<String, Object>> uiEventSink) {
        this.uiEventSink = uiEventSink;
    }
    
    @Override
    public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
        log.info("[Tool Monitor Advisor] 开始请求，可用工具数：{}", 
                 request.tools() != null ? request.tools().size() : 0);
        
        // 记录请求开始时间
        context.put("startTime", System.currentTimeMillis());
        
        return request;
    }
    
    @Override
    public AdvisedResponse adviseResponse(AdvisedResponse response, Map<String, Object> context) {
        Long startTime = (Long) context.get("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
        
        log.info("[Tool Monitor Advisor] 请求完成，耗时：{}ms", duration);
        
        // 检查是否有工具调用
        if (response.result() != null && response.result().getMetadata() != null) {
            var metadata = response.result().getMetadata();
            
            // 工具调用信息（需要验证实际字段名）
            Object toolCalls = metadata.get("tool_calls");
            if (toolCalls != null) {
                log.info("[Tool Monitor Advisor] 检测到工具调用：{}", toolCalls);
                emitToolCallEvent(toolCalls, duration);
            }
        }
        
        return response;
    }
    
    private void emitToolCallEvent(Object toolCalls, long duration) {
        if (uiEventSink == null) return;
        
        Map<String, Object> event = new HashMap<>();
        event.put("event", "tool_monitor");
        event.put("payload", Map.of(
            "toolCalls", toolCalls,
            "durationMs", duration,
            "timestamp", System.currentTimeMillis()
        ));
        
        uiEventSink.tryEmitNext(event);
    }
    
    @Override
    public String getName() {
        return "ToolMonitorAdvisor";
    }
    
    @Override
    public int getOrder() {
        return 0;  // 最高优先级，最先执行
    }
}
```

### 第 2 步：修改 `AiAssistantController`

```java
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    // ... 前面的代码不变 ...
    
    // 构建工具
    JobTool jobTool = new JobTool(jobService, dataSourceService);
    jobTool.setUiEventSink(uiEventSink);
    DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);
    
    // ✅ 新方式：使用 Advisor
    ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(jobTool, dataSourceTool)  // 不再需要 wrapTools
        .defaultAdvisors(
            new ToolMonitorAdvisor(uiEventSink)  // 添加监控 Advisor
        )
        .build();
    
    // ❌ 旧方式：删除这段
    // ToolCallback[] monitoredTools = ToolMonitorConfig.wrapTools(uiEventSink, jobTool, dataSourceTool);
    // ChatClient chatClient = ChatClient.builder(chatModel)
    //     .defaultToolCallbacks(monitoredTools)
    //     .build();
    
    // ... 后面的代码不变 ...
}
```

### 第 3 步：验证元数据格式

需要运行一次实际调用，打印 `metadata` 看看 Spring AI 实际返回的格式：

```java
@Override
public AdvisedResponse adviseResponse(AdvisedResponse response, Map<String, Object> context) {
    if (response.result() != null && response.result().getMetadata() != null) {
        var metadata = response.result().getMetadata();
        
        // 🔍 调试：打印所有元数据
        log.info("[Tool Monitor Advisor] 元数据：{}", metadata);
        
        // 根据实际格式调整解析逻辑
    }
    return response;
}
```

### 第 4 步：清理旧代码（可选）

如果 Advisor 方案运行正常，可以删除：
- `com.dataplatform.ai.ObservableToolCallback`
- `com.dataplatform.ai.ToolMonitorConfig`

---

## 方案对比总结

| 特性 | 装饰器模式 | Spring AI Advisor |
|-----|-----------|-------------------|
| 代码量 | 中等 | 中等 |
| 样板代码 | 多（需 wrapTools） | 少 |
| 框架兼容 | 非标准 | 原生支持 |
| 监控粒度 | 工具调用 | 整个请求/响应 |
| 组合能力 | 弱 | 强（可链式组合） |
| 未来兼容 | 不确定 | 好 |
| 学习成本 | 低 | 中 |

---

## 建议

### 短期（立即行动）

1. **验证 Advisor 可行性**（30分钟）
   - 创建 `ToolMonitorAdvisor`
   - 在一个端点测试
   - 验证元数据格式

2. **如果可行，逐步迁移**（1小时）
   - 修改 `/chat/stream` 端点
   - 保留装饰器代码作为备份
   - 测试功能完整性

### 长期

- **监控元数据** - 使用 Advisor 获取请求/响应的全局信息
- **工具表格推送** - 工具方法内部仍使用 `uiEventSink` 推送表格
- **组合其他 Advisor** - 后续可以添加限流、重试等 Advisor

---

## 注意事项

1. **元数据格式不确定** - Spring AI 文档未明确说明 `tool_calls` 的格式，需要实际测试
2. **工具内部推送仍需 Sink** - Advisor 只能监控调用，表格数据仍需工具内部推送
3. **向后兼容** - 保留装饰器代码直到 Advisor 方案完全稳定

---

## 结论

**推荐使用 Spring AI Advisor**：
- ✅ 更符合框架设计
- ✅ 零样板代码
- ✅ 功能更强大
- ✅ 未来兼容性好

**立即行动**：先创建 `ToolMonitorAdvisor` 验证可行性（30分钟），可行则迁移，不可行则保留装饰器。
