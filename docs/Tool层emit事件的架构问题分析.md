# @Tool 方法中直接 emit 事件的架构问题分析

## 当前架构

```java
@Component("jobTool")
public class JobTool {
    
    // ❌ 问题：工具类持有 UI 层的 Sink
    private Sinks.Many<Map<String, Object>> uiEventSink;
    
    public void setUiEventSink(Sinks.Many<Map<String, Object>> sink) {
        this.uiEventSink = sink;
    }
    
    @Tool(description = "查询最近的作业列表")
    public String listRecentJobs(@ToolParam Integer limit) {
        // 业务逻辑
        List<Job> jobs = jobService.page(request).getRecords();
        
        // ❌ 问题：业务逻辑直接推送 UI 事件
        emit("table_start", Map.of("type", "job_list", "total", jobs.size()));
        for (Job job : jobs) {
            emit("table_row", Map.of("row", convertToMap(job)));
        }
        emit("table_end", Map.of());
        
        return "查询到 " + jobs.size() + " 条记录";
    }
    
    private void emit(String event, Object payload) {
        if (uiEventSink != null) {
            uiEventSink.tryEmitNext(Map.of("event", event, "payload", payload));
        }
    }
}
```

```java
// Controller 中注入 Sink
@GetMapping("/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();
    
    JobTool jobTool = new JobTool(jobService, dataSourceService);
    jobTool.setUiEventSink(uiEventSink);  // ❌ 手动注入
    
    // ...
}
```

---

## 问题分析

### 1. **违反分层架构原则**

```
┌─────────────────────────────────────────┐
│         Controller Layer (UI)           │
│  - SSE Event Sink                       │
│  - HTTP Response                        │
└───────────────┬─────────────────────────┘
                │
                │ ❌ 直接依赖（反向依赖）
                ↓
┌─────────────────────────────────────────┐
│         Tool Layer (Business Logic)     │
│  - @Tool methods                        │
│  - emit("table_row", ...)               │ ← ❌ 不应该知道 UI 事件格式
└───────────────┬─────────────────────────┘
                │
                ↓
┌─────────────────────────────────────────┐
│         Service Layer                   │
│  - JobService                           │
└─────────────────────────────────────────┘
```

**问题**：
- Tool 层不应该知道 UI 层的 `Sinks.Many`、`"table_row"` 等概念
- Tool 应该专注于业务逻辑，返回纯数据
- UI 展现形式应该由 Controller/Advisor 决定

### 2. **线程安全问题**

```java
@Component("jobTool")  // ❌ 单例 Bean
public class JobTool {
    private Sinks.Many<Map<String, Object>> uiEventSink;  // ❌ 实例变量
    
    // 请求 A：setUiEventSink(sinkA)
    // 请求 B：setUiEventSink(sinkB)  ← 覆盖了 sinkA
    // 请求 A 的事件发送到 sinkB ← ❌ 数据泄露
}
```

**问题**：
- `JobTool` 是单例，多个请求共享同一个实例
- `setUiEventSink()` 会被并发请求覆盖
- **可能导致请求 A 的数据发送到请求 B 的前端**

### 3. **难以测试**

```java
@Test
public void testListRecentJobs() {
    JobTool jobTool = new JobTool(jobService, dataSourceService);
    
    // ❌ 需要 mock Sink
    Sinks.Many<Map<String, Object>> mockSink = Sinks.many().unicast().onBackpressureBuffer();
    jobTool.setUiEventSink(mockSink);
    
    String result = jobTool.listRecentJobs(10);
    
    // ❌ 需要验证 Sink 中的事件
    // 如何验证？如何 assert？
}
```

### 4. **耦合性高，扩展性差**

如果要支持新的前端展示方式：
- WebSocket 而不是 SSE？ ❌ 需要修改 Tool 代码
- 返回 JSON 而不是事件流？ ❌ 需要修改 Tool 代码
- 添加日志监控？ ❌ 需要在每个 `emit()` 调用处添加

---

## 更好的架构方案

### 方案 1：Tool 返回纯数据 + Advisor 负责格式化（推荐）

```java
// ============ Tool 层：只负责业务逻辑 ============
@Component("jobTool")
public class JobTool {
    
    private final JobService jobService;
    
    @Tool(description = "查询最近的作业列表")
    public JobListResult listRecentJobs(@ToolParam Integer limit) {
        // 只做业务逻辑，返回纯数据
        List<Job> jobs = jobService.page(request).getRecords();
        return new JobListResult(jobs);  // ✅ 返回领域对象
    }
    
    // ✅ 领域模型：业务数据结构
    public record JobListResult(List<Job> jobs) {}
}

// ============ Advisor 层：负责 UI 事件转换 ============
public class ToolResultStreamAdvisor implements Advisor {
    
    private final Sinks.Many<Map<String, Object>> uiEventSink;
    
    @Override
    public AdvisedResponse adviseResponse(AdvisedResponse response, Map<String, Object> context) {
        // 检查工具调用结果
        if (response.result() != null) {
            Object toolResult = response.result().getOutput();
            
            // ✅ 根据类型分发事件
            if (toolResult instanceof JobListResult jobListResult) {
                emitJobListAsTable(jobListResult.jobs());
            } else if (toolResult instanceof DataSourceListResult dsListResult) {
                emitDataSourceListAsTable(dsListResult.dataSources());
            }
        }
        return response;
    }
    
    private void emitJobListAsTable(List<Job> jobs) {
        emit("table_start", Map.of("type", "job_list", "total", jobs.size()));
        for (Job job : jobs) {
            emit("table_row", Map.of("row", convertJobToMap(job)));
        }
        emit("table_end", Map.of());
    }
    
    private void emit(String event, Object payload) {
        if (uiEventSink != null) {
            uiEventSink.tryEmitNext(Map.of("event", event, "payload", payload));
        }
    }
    
    private Map<String, Object> convertJobToMap(Job job) {
        return Map.of(
            "id", job.getId(),
            "jobName", job.getJobName(),
            "status", job.getStatus().toString(),
            // ...
        );
    }
}

// ============ Controller 层 ============
@GetMapping("/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();
    
    // ✅ Tool 不需要注入 Sink
    JobTool jobTool = new JobTool(jobService, dataSourceService);
    DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);
    
    // ✅ Advisor 负责格式化
    ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(jobTool, dataSourceTool)
        .defaultAdvisors(
            new ToolResultStreamAdvisor(uiEventSink)  // UI 格式化逻辑
        )
        .build();
    
    // ...
}
```

**优点**：
✅ **分层清晰** - Tool 只关心业务，Advisor 只关心展示  
✅ **易测试** - Tool 返回纯数据，无需 mock Sink  
✅ **可扩展** - 新增展示格式只需修改 Advisor  
✅ **无线程安全问题** - Tool 是无状态的  

---

### 方案 2：Tool 发布 Spring Event + EventListener 处理

```java
// ============ Tool 层：发布领域事件 ============
@Component("jobTool")
public class JobTool {
    
    private final ApplicationEventPublisher eventPublisher;
    
    @Tool(description = "查询最近的作业列表")
    public String listRecentJobs(@ToolParam Integer limit) {
        List<Job> jobs = jobService.page(request).getRecords();
        
        // ✅ 发布领域事件（不知道 UI 层）
        eventPublisher.publishEvent(new JobListQueriedEvent(jobs));
        
        return "查询到 " + jobs.size() + " 条记录";
    }
}

// ============ 领域事件 ============
public record JobListQueriedEvent(List<Job> jobs) {}

// ============ EventListener：负责 UI 推送 ============
@Component
public class ToolEventToUiStreamListener {
    
    @EventListener
    public void onJobListQueried(JobListQueriedEvent event) {
        // 从 ThreadLocal 或 Request Scope 获取 Sink
        Sinks.Many<Map<String, Object>> sink = UiEventContext.getCurrentSink();
        if (sink != null) {
            emitJobListAsTable(event.jobs(), sink);
        }
    }
    
    private void emitJobListAsTable(List<Job> jobs, Sinks.Many<Map<String, Object>> sink) {
        sink.tryEmitNext(Map.of("event", "table_start", "payload", Map.of("total", jobs.size())));
        for (Job job : jobs) {
            sink.tryEmitNext(Map.of("event", "table_row", "payload", Map.of("row", convertJobToMap(job))));
        }
        sink.tryEmitNext(Map.of("event", "table_end", "payload", Map.of()));
    }
}

// ============ ThreadLocal 上下文 ============
public class UiEventContext {
    private static final ThreadLocal<Sinks.Many<Map<String, Object>>> sinkHolder = new ThreadLocal<>();
    
    public static void setSink(Sinks.Many<Map<String, Object>> sink) {
        sinkHolder.set(sink);
    }
    
    public static Sinks.Many<Map<String, Object>> getCurrentSink() {
        return sinkHolder.get();
    }
    
    public static void clear() {
        sinkHolder.remove();
    }
}

// ============ Controller ============
@GetMapping("/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();
    
    // ✅ 设置 ThreadLocal
    UiEventContext.setSink(uiEventSink);
    
    ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(jobTool, dataSourceTool)
        .build();
    
    return chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(message)
        .stream()
        .chatResponse()
        .mapNotNull(/* ... */)
        .doFinally(signal -> UiEventContext.clear());  // ✅ 清理
}
```

**优点**：
✅ **完全解耦** - Tool 不依赖任何 UI 层  
✅ **Spring 原生** - 使用标准的事件机制  
✅ **可观察** - 可以添加多个 EventListener 做监控/日志  

**缺点**：
⚠️ **异步问题** - Spring Event 默认同步，但容易被误用为异步  
⚠️ **ThreadLocal 陷阱** - Reactive 编程中 ThreadLocal 可能失效  

---

### 方案 3：混合方案（实用主义）

```java
// ============ Tool 层：支持流式回调（可选） ============
@Component("jobTool")
public class JobTool {
    
    @Tool(description = "查询最近的作业列表")
    public JobListResult listRecentJobs(
        @ToolParam Integer limit,
        @ToolParam(required = false) StreamCallback callback  // ✅ 可选回调
    ) {
        List<Job> jobs = jobService.page(request).getRecords();
        
        // ✅ 如果提供了回调，流式推送
        if (callback != null) {
            callback.onStart("job_list", jobs.size());
            for (Job job : jobs) {
                callback.onRow(job);
            }
            callback.onEnd();
        }
        
        // ✅ 总是返回完整数据
        return new JobListResult(jobs);
    }
}

// ============ 回调接口 ============
public interface StreamCallback<T> {
    void onStart(String type, int total);
    void onRow(T row);
    void onEnd();
}

// ============ Advisor 提供回调实现 ============
public class ToolStreamAdvisor implements Advisor {
    
    @Override
    public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
        // ✅ 注入回调到工具调用参数中
        StreamCallback callback = new SinkStreamCallback(uiEventSink);
        
        // 修改请求，添加回调参数
        // （需要 Spring AI 支持动态参数注入）
        
        return request;
    }
}
```

**问题**：Spring AI 可能不支持动态注入 `@ToolParam`

---

## 方案对比

| 特性 | 当前方案 | 方案1: Advisor | 方案2: Event | 方案3: 回调 |
|-----|---------|---------------|-------------|-----------|
| 分层清晰 | ❌ | ✅ | ✅ | ⚠️ |
| 线程安全 | ❌ | ✅ | ⚠️ | ✅ |
| 易测试 | ❌ | ✅ | ✅ | ⚠️ |
| 易扩展 | ❌ | ✅ | ✅ | ⚠️ |
| 实现复杂度 | 低 | 中 | 中 | 高 |
| Spring AI 兼容 | ✅ | ✅ | ✅ | ❌ |

---

## 推荐方案

### 短期（立即修复线程安全问题）

**最小改动**：使用 `ThreadLocal` 或 `Request Scope Bean`

```java
// 1. 创建 Request Scope Bean
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestUiEventSink {
    private Sinks.Many<Map<String, Object>> sink;
    
    public void setSink(Sinks.Many<Map<String, Object>> sink) {
        this.sink = sink;
    }
    
    public Sinks.Many<Map<String, Object>> getSink() {
        return sink;
    }
}

// 2. Tool 注入 Request Scope Bean
@Component("jobTool")
public class JobTool {
    
    @Autowired
    private RequestUiEventSink requestSink;  // ✅ 每个请求独立
    
    private void emit(String event, Object payload) {
        Sinks.Many<Map<String, Object>> sink = requestSink.getSink();
        if (sink != null) {
            sink.tryEmitNext(Map.of("event", event, "payload", payload));
        }
    }
}

// 3. Controller 设置 Sink
@GetMapping("/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();
    
    requestUiEventSink.setSink(uiEventSink);  // ✅ 设置当前请求的 Sink
    
    // ...
}
```

### 长期（架构重构）

**推荐方案 1: Tool 返回数据 + Advisor 格式化**

理由：
1. ✅ 分层最清晰
2. ✅ Tool 易测试（返回纯数据）
3. ✅ 符合 Spring AI 设计理念
4. ✅ 扩展性最好（新增展示格式不影响 Tool）

---

## 结论

**当前架构的核心问题**：
1. ❌ **严重的线程安全问题** - 可能导致数据泄露
2. ❌ 违反分层架构 - Tool 层不应该知道 UI 事件
3. ❌ 难以测试和扩展

**立即行动**：
1. **紧急修复** - 使用 Request Scope Bean 或 ThreadLocal（1小时）
2. **计划重构** - 迁移到 "Tool返回数据 + Advisor格式化" 架构（2-3小时）
