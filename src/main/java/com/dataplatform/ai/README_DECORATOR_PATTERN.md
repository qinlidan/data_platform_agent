# AI 工具监控 - 装饰器模式实现

## 设计概览

这是一个**生产级的装饰器模式（Decorator Pattern）**实现，用于为 Spring AI 的工具调用添加可观测性，而无需修改原有工具类的代码。

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    AiAssistantController                     │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ 使用工厂方法包装工具：                                      │ │
│  │ ToolCallback[] tools = ToolMonitorConfig.wrapTools(     │ │
│  │     eventSink, jobTool, dataSourceTool);                │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
          ┌────────────────────────────────┐
          │     ToolMonitorConfig          │
          │ (工厂类，简化包装逻辑)           │
          └────────────┬───────────────────┘
                       │ 创建
                       ▼
    ┌──────────────────────────────────────────┐
    │      ObservableToolCallback              │
    │    (装饰器，实现 ToolCallback 接口)        │
    │  ┌────────────────────────────────────┐  │
    │  │ - delegate: ToolCallback (原工具)   │  │
    │  │ - uiEventSink: Sinks.Many (事件通道)│  │
    │  │                                     │  │
    │  │ call(input):                        │  │
    │  │   1. emitToolStart()  [前置增强]    │  │
    │  │   2. delegate.call()  [原始调用]    │  │
    │  │   3. emitToolEnd()    [后置增强]    │  │
    │  └────────────────────────────────────┘  │
    └──────────────┬───────────────────────────┘
                   │ 委托
                   ▼
       ┌──────────────────────┐
       │  JobTool (原工具类)   │
       │  @Tool 方法列表       │
       └──────────────────────┘
```

## 核心类说明

### 1. ObservableToolCallback (装饰器)

**职责**：在不修改原工具类的前提下，拦截工具调用并发送监控事件。

**关键特性**：
- ✅ **实现相同接口**：`implements ToolCallback`
- ✅ **持有原对象引用**：`private final ToolCallback delegate`
- ✅ **透明代理**：调用前后添加行为，但不改变输入输出
- ✅ **异常处理**：捕获异常后仍抛出，保持原有错误传播链
- ✅ **容错性**：`uiEventSink` 可为 null，降级为仅日志记录

**监控能力**：
- 工具调用开始/结束事件
- 执行耗时统计
- 异常监控（失败时发送 `error` 字段）
- 时间戳记录

```java
// 装饰器模式的经典结构
public class ObservableToolCallback implements ToolCallback {
    private final ToolCallback delegate; // 被装饰对象
    
    public String call(String input) {
        // 1. 前置增强
        emitToolStart(...);
        
        try {
            // 2. 委托原对象执行
            String result = delegate.call(input);
            
            // 3. 后置增强（成功）
            emitToolEnd(..., null);
            return result;
        } catch (Exception e) {
            // 3. 后置增强（失败）
            emitToolEnd(..., errorMessage);
            throw e; // 保持异常传播
        }
    }
}
```

### 2. ToolMonitorConfig (工厂类)

**职责**：提供简洁的 API，批量包装工具。

**优势**：
- 隐藏复杂的包装逻辑（从工具对象 → ToolCallback[] → 包装后数组）
- 一行代码完成批量包装
- 统一日志记录

```java
// 使用前（手动包装，冗长）
ToolCallback[] callbacks1 = ToolCallbacks.from(jobTool);
ToolCallback[] callbacks2 = ToolCallbacks.from(dataSourceTool);
ToolCallback[] wrapped = new ToolCallback[callbacks1.length + callbacks2.length];
for (int i = 0; i < callbacks1.length; i++) {
    wrapped[i] = new ObservableToolCallback(callbacks1[i], eventSink);
}
// ... 更多循环

// 使用后（工厂方法，简洁）
ToolCallback[] wrapped = ToolMonitorConfig.wrapTools(eventSink, jobTool, dataSourceTool);
```

### 3. Controller 使用示例

```java
// 创建事件通道
Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();

// 构建原工具
JobTool jobTool = new JobTool(jobService, dataSourceService);
DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);

// 一键包装（装饰器模式）
ToolCallback[] monitoredTools = ToolMonitorConfig.wrapTools(uiEventSink, jobTool, dataSourceTool);

// 传给 ChatClient
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultToolCallbacks(monitoredTools)
        .build();
```

## 设计模式分析

### 为什么是装饰器模式？

**符合装饰器模式的经典定义**：
1. ✅ **动态扩展**：运行时为对象添加新行为（监控能力）
2. ✅ **透明性**：客户端无法区分装饰器和原对象（都实现 `ToolCallback`）
3. ✅ **可组合**：理论上可以多层装饰（如再套一个 `MetricsToolCallback`）
4. ✅ **职责分离**：监控逻辑与业务逻辑完全解耦

### 与其他模式的对比

| 模式 | 结构特征 | 本实现是否符合 |
|------|---------|---------------|
| **装饰器（Decorator）** | 实现相同接口，持有原对象，调用前后增强 | ✅ 完全符合 |
| 代理（Proxy） | 控制对象访问，可能延迟加载/权限控制 | ❌ 我们不控制访问，只是增强 |
| 适配器（Adapter） | 转换接口，使不兼容类协同工作 | ❌ 我们不转换接口 |
| 策略（Strategy） | 封装算法，运行时切换实现 | ❌ 我们不替换算法 |

## 生产级特性

### ✅ 已实现

1. **空安全**：
   - 构造器参数验证：`if (delegate == null) throw ...`
   - `uiEventSink` 可为 null，降级为仅日志

2. **异常处理**：
   - 捕获工具调用异常，发送失败事件
   - 重新抛出异常，保持调用链完整性
   - 事件发送失败不影响工具调用

3. **日志记录**：
   - 统一使用 `[Tool Monitor]` 前缀
   - 记录工具名、耗时、成功/失败状态

4. **性能考量**：
   - 使用 `System.currentTimeMillis()` 而非 `Instant`（更快）
   - 事件发送失败时 `warn` 而非 `error`（避免日志风暴）

5. **JavaDoc 文档**：
   - 类级别说明设计模式
   - 方法级别说明参数含义

### 🔧 可选增强（根据需求）

1. **指标收集**（Metrics）：
   ```java
   private void recordMetrics(String toolName, long duration, boolean success) {
       meterRegistry.timer("ai.tool.call", 
           "tool", toolName, 
           "success", String.valueOf(success)
       ).record(Duration.ofMillis(duration));
   }
   ```

2. **分布式追踪**（Tracing）：
   ```java
   Span span = tracer.spanBuilder("ai.tool." + toolName).startSpan();
   try (Scope scope = span.makeCurrent()) {
       // ... 工具调用
   } finally {
       span.end();
   }
   ```

3. **限流/熔断**（Resilience）：
   ```java
   @CircuitBreaker(name = "aiTools", fallbackMethod = "toolFallback")
   public String call(String input) { ... }
   ```

4. **审计日志**（Audit）：
   ```java
   auditLogger.log(AuditEvent.builder()
       .type("TOOL_CALL")
       .toolName(toolName)
       .userId(SecurityContextHolder.getContext().getAuthentication().getName())
       .timestamp(Instant.now())
       .build());
   ```

## 事件流

```
前端发送消息 → Controller
                  │
                  ├─ 创建 uiEventSink (SSE 通道)
                  │
                  ├─ 包装工具 (ObservableToolCallback)
                  │
                  ├─ ChatClient 调用 AI
                  │
                  ├─ AI 决策：需要调用工具 listRecentJobs
                  │
                  ▼
            ObservableToolCallback.call()
                  │
                  ├─ emitToolStart("listRecentJobs", input)
                  │  └─ SSE → 前端显示「🔧 正在调用工具...」
                  │
                  ├─ delegate.call() → JobTool.listRecentJobs()
                  │  └─ 查询数据库，返回结果
                  │
                  ├─ emitToolEnd("listRecentJobs", result, 1200ms, null)
                  │  └─ SSE → 前端隐藏徽章
                  │
                  ▼
            结果返回给 AI → AI 组织自然语言回复 → 前端显示
```

## 前端效果

用户发送：「查看最近的作业列表」

前端显示：
```
┌──────────────────────────────────────────┐
│ [AI]                                      │
│  ┌────────────────────────────────────┐  │
│  │ 🔧 正在调用工具：查询最近的作业列表   │  │  ← 蓝色徽章（tool_start）
│  └────────────────────────────────────┘  │
│  ↓ (1.2秒后，工具返回)                    │
│  好的，以下是最近的作业列表：             │  ← AI 回复（tool_end 后）
│  ┌──────┬────────┬────────┐             │
│  │ 作业 │ 状态   │ 时间   │             │
│  ├──────┼────────┼────────┤             │
│  │ job1 │ 运行中 │ 10:00  │             │
│  └──────┴────────┴────────┘             │
└──────────────────────────────────────────┘
```

## 测试建议

### 单元测试

```java
@Test
void testObservableToolCallback_Success() {
    // Given
    ToolCallback mockDelegate = mock(ToolCallback.class);
    when(mockDelegate.call("input")).thenReturn("output");
    
    Sinks.Many<Map<String, Object>> sink = Sinks.many().replay().all();
    ObservableToolCallback wrapper = new ObservableToolCallback(mockDelegate, sink);
    
    // When
    String result = wrapper.call("input");
    
    // Then
    assertEquals("output", result);
    verify(mockDelegate).call("input");
    
    List<Map<String, Object>> events = sink.asFlux().collectList().block();
    assertEquals(2, events.size()); // tool_start + tool_end
    assertEquals("tool_start", events.get(0).get("event"));
    assertEquals("tool_end", events.get(1).get("event"));
}

@Test
void testObservableToolCallback_Exception() {
    // Given
    ToolCallback mockDelegate = mock(ToolCallback.class);
    when(mockDelegate.call("input")).thenThrow(new RuntimeException("Tool failed"));
    
    Sinks.Many<Map<String, Object>> sink = Sinks.many().replay().all();
    ObservableToolCallback wrapper = new ObservableToolCallback(mockDelegate, sink);
    
    // When/Then
    assertThrows(RuntimeException.class, () -> wrapper.call("input"));
    
    List<Map<String, Object>> events = sink.asFlux().collectList().block();
    Map<String, Object> endEvent = events.get(1);
    assertEquals("tool_end", endEvent.get("event"));
    assertEquals(false, ((Map) endEvent.get("payload")).get("success"));
    assertEquals("Tool failed", ((Map) endEvent.get("payload")).get("error"));
}
```

### 集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class AiAssistantIntegrationTest {
    
    @Test
    void testToolCallWithMonitoring() throws Exception {
        mockMvc.perform(get("/api/ai/chat/stream")
                .param("message", "查询最近的作业列表"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/event-stream"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertTrue(body.contains("tool_start"));
                    assertTrue(body.contains("listRecentJobs"));
                });
    }
}
```

## 总结

这是一个**生产级的装饰器模式实现**，具备以下优势：

### 优点

1. **符合 SOLID 原则**：开闭原则、单一职责、依赖倒置
2. **无侵入性**：不修改原工具类代码
3. **可扩展性**：易于添加新的监控维度（指标、追踪、审计）
4. **易用性**：工厂方法 `ToolMonitorConfig.wrapTools()` 简化使用
5. **健壮性**：空安全、异常处理、日志记录
6. **可测试性**：依赖注入，易于 Mock

### 适用场景

- ✅ 需要为现有工具添加横切关注点（监控、日志、指标）
- ✅ 不想修改原有工具类代码（开闭原则）
- ✅ 需要灵活地启用/禁用监控（传 null 即禁用）

### 不适用场景

- ❌ 需要在编译期织入代码（用 AOP 或注解处理器）
- ❌ 需要拦截所有方法调用（用动态代理）
- ❌ 需要修改方法签名或返回值类型（用适配器模式）

---

**作者**：Data Platform Team  
**更新时间**：2026-10-22
