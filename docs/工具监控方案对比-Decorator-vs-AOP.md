# 工具监控实现方案对比：Decorator vs AOP

## 当前实现：装饰器模式 (Decorator Pattern)

### 实现方式

```java
// 1. 装饰器类
public class ObservableToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    
    @Override
    public String call(String toolInput) {
        log.info("开始调用工具");
        long startTime = System.currentTimeMillis();
        try {
            String result = delegate.call(toolInput);
            log.info("调用成功，耗时: {}ms", System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("调用失败", e);
            throw e;
        }
    }
}

// 2. 手动包装
ToolCallback[] tools = ToolMonitorConfig.wrapTools(eventSink, jobTool, dsTool);
```

### 优点
✅ **精确控制** - 只监控需要监控的工具  
✅ **框架兼容** - 完美适配 Spring AI 的 ToolCallback 接口  
✅ **无侵入** - 不修改原工具类  
✅ **灵活性高** - 可以为不同工具定制不同的监控策略  

### 缺点
❌ **手动包装** - 需要显式调用 `wrapTools()`  
❌ **样板代码** - 每个使用工具的地方都要包装  
❌ **易遗漏** - 新增工具可能忘记包装  

---

## 建议方案：AOP (Aspect-Oriented Programming)

### 方案 A：拦截工具类方法（推荐）

```java
@Aspect
@Component
@Slf4j
public class ToolMonitorAspect {
    
    @Autowired(required = false)
    private Sinks.Many<Map<String, Object>> uiEventSink;
    
    /**
     * 拦截所有带 @Tool 注解的方法
     */
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object monitorToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        
        String toolName = method.getName();
        String description = toolAnnotation.description();
        Object[] args = joinPoint.getArgs();
        
        log.info("[Tool Monitor AOP] 开始调用工具: {} - {}", toolName, description);
        emitToolStart(toolName, args);
        
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Tool Monitor AOP] 工具调用成功: {} (耗时: {}ms)", toolName, duration);
            emitToolEnd(toolName, result, duration, null);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Tool Monitor AOP] 工具调用失败: {} (耗时: {}ms)", toolName, duration, e);
            emitToolEnd(toolName, null, duration, e.getMessage());
            throw e;
        }
    }
    
    private void emitToolStart(String toolName, Object[] args) {
        if (uiEventSink == null) return;
        
        Map<String, Object> event = new HashMap<>();
        event.put("event", "tool_start");
        event.put("payload", Map.of(
            "toolName", toolName,
            "args", Arrays.toString(args),
            "timestamp", System.currentTimeMillis()
        ));
        uiEventSink.tryEmitNext(event);
    }
    
    private void emitToolEnd(String toolName, Object result, 
                             long durationMs, String errorMessage) {
        if (uiEventSink == null) return;
        
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
        }
        event.put("payload", payload);
        uiEventSink.tryEmitNext(event);
    }
}
```

### 使用方式

```java
// 不需要任何包装代码！
@Component("jobTool")
public class JobTool {
    
    @Tool(description = "查询作业详情")
    public String getJobDetails(@ToolParam(description = "作业ID") Long jobId) {
        // 业务逻辑
        // AOP 自动拦截并监控
    }
    
    @Tool(description = "创建作业")
    public String createJob(...) {
        // 业务逻辑
        // AOP 自动拦截并监控
    }
}

// Controller 中直接使用，无需包装
@RestController
public class AIChatController {
    
    @Autowired
    private JobTool jobTool;
    
    @Autowired
    private DataSourceTool dataSourceTool;
    
    @GetMapping("/chat")
    public Flux<ServerSentEvent<String>> chat() {
        // 直接传给 ChatClient，AOP 自动生效
        return chatClient.prompt()
            .functions(jobTool, dataSourceTool)  // 无需 wrapTools
            .stream()
            .content();
    }
}
```

### 优点
✅ **零样板代码** - 不需要手动包装  
✅ **自动生效** - 所有 @Tool 方法自动监控  
✅ **不易遗漏** - 新增工具自动纳入监控  
✅ **声明式** - 关注点分离更清晰  
✅ **易维护** - 监控逻辑集中在一处  
✅ **更 Spring 化** - 符合 Spring 的编程模型  

### 潜在问题及解决

**问题 1**: Spring AI 可能绕过 Spring Bean 代理直接调用方法

**解决**: 
- 只要 `JobTool` 是 `@Component`，Spring AOP 会为其创建代理
- Spring AI 通过 Spring 容器获取工具实例，会拿到代理对象
- 验证方式：启动后检查 `jobTool.getClass()` 是否包含 `$$EnhancerBySpringCGLIB$$`

**问题 2**: 每个请求的 `uiEventSink` 不同

**解决方案 1**: 使用 `ThreadLocal` 传递
```java
public class ToolMonitorContext {
    private static final ThreadLocal<Sinks.Many<Map<String, Object>>> sinkHolder 
        = new ThreadLocal<>();
    
    public static void setSink(Sinks.Many<Map<String, Object>> sink) {
        sinkHolder.set(sink);
    }
    
    public static Sinks.Many<Map<String, Object>> getSink() {
        return sinkHolder.get();
    }
    
    public static void clear() {
        sinkHolder.remove();
    }
}

// 在 Controller 中设置
@GetMapping("/chat")
public Flux<ServerSentEvent<String>> chat() {
    Sinks.Many<Map<String, Object>> sink = Sinks.many().replay().all();
    ToolMonitorContext.setSink(sink);  // 设置当前线程的 Sink
    
    return chatClient.prompt()
        .functions(jobTool, dataSourceTool)
        .stream()
        .content()
        .doFinally(signal -> ToolMonitorContext.clear());  // 清理
}
```

**解决方案 2**: 使用 Request Scope Bean
```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestEventSink {
    private Sinks.Many<Map<String, Object>> sink;
    
    public void setSink(Sinks.Many<Map<String, Object>> sink) {
        this.sink = sink;
    }
    
    public Sinks.Many<Map<String, Object>> getSink() {
        return sink;
    }
}

@Aspect
@Component
public class ToolMonitorAspect {
    @Autowired
    private RequestEventSink requestEventSink;
    
    // 使用 requestEventSink.getSink() 发送事件
}
```

---

## 方案 B：混合模式（最佳实践）

结合两者优势：

```java
// 1. AOP 负责通用监控（日志、耗时）
@Aspect
@Component
@Slf4j
public class ToolMonitorAspect {
    
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object monitorToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        log.info("[Tool Monitor] 开始调用: {}", toolName);
        
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Tool Monitor] 调用成功: {} (耗时: {}ms)", toolName, duration);
            
            // 发布 Spring Event
            applicationEventPublisher.publishEvent(
                new ToolCallSuccessEvent(toolName, duration, result)
            );
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Tool Monitor] 调用失败: {} (耗时: {}ms)", toolName, duration, e);
            
            // 发布 Spring Event
            applicationEventPublisher.publishEvent(
                new ToolCallFailureEvent(toolName, duration, e)
            );
            
            throw e;
        }
    }
}

// 2. EventListener 负责 UI 事件推送（可选）
@Component
public class ToolEventListener {
    
    @EventListener
    public void onToolCallSuccess(ToolCallSuccessEvent event) {
        Sinks.Many<Map<String, Object>> sink = ToolMonitorContext.getSink();
        if (sink != null) {
            sink.tryEmitNext(Map.of(
                "event", "tool_end",
                "payload", Map.of(
                    "toolName", event.getToolName(),
                    "durationMs", event.getDuration(),
                    "success", true
                )
            ));
        }
    }
    
    @EventListener
    public void onToolCallFailure(ToolCallFailureEvent event) {
        Sinks.Many<Map<String, Object>> sink = ToolMonitorContext.getSink();
        if (sink != null) {
            sink.tryEmitNext(Map.of(
                "event", "tool_end",
                "payload", Map.of(
                    "toolName", event.getToolName(),
                    "durationMs", event.getDuration(),
                    "success", false,
                    "error", event.getException().getMessage()
                )
            ));
        }
    }
}

// 3. 可选：保留 Decorator 用于特殊需求
// 比如某些工具需要特殊的监控逻辑
```

### 优点
✅ **层次清晰** - AOP 做通用监控，Event 做定制化  
✅ **解耦合** - 监控、日志、UI推送各司其职  
✅ **易测试** - 可以独立测试每个组件  
✅ **可扩展** - 新增监控需求只需添加 EventListener  

---

## 对比总结

| 特性 | Decorator 模式 | AOP 方案 | 混合模式 |
|-----|---------------|---------|---------|
| 代码量 | 中等 | 少 | 中等 |
| 样板代码 | 多（需手动包装）| 无 | 无 |
| 自动生效 | ❌ | ✅ | ✅ |
| 灵活性 | 高 | 中 | 高 |
| 框架兼容性 | 完美 | 需验证 | 完美 |
| 易理解性 | 高 | 中 | 中 |
| 可维护性 | 中 | 高 | 高 |
| 可测试性 | 高 | 高 | 高 |
| Spring 风格 | 中 | 高 | 高 |

---

## 推荐方案

### 阶段 1：快速验证 AOP 可行性（1小时）

1. 保留现有 Decorator 代码
2. 创建 `ToolMonitorAspect`
3. 启动应用，调用工具，验证 AOP 是否生效
4. 检查日志中是否有 `[Tool Monitor AOP]` 输出

### 阶段 2：完整迁移到 AOP（如果可行，2小时）

1. 实现 ThreadLocal 或 Request Scope 的 Sink 管理
2. 在 AOP 中添加 UI 事件推送
3. 移除 `ObservableToolCallback` 和 `ToolMonitorConfig`
4. 重构 Controller，移除 `wrapTools()` 调用

### 阶段 3：优化为混合模式（可选，2小时）

1. AOP 只做通用监控和日志
2. 使用 Spring Event 解耦
3. 为不同场景添加不同的 EventListener

---

## 结论

**建议采用 AOP 方案**，理由：

1. ✅ **更符合 Spring 生态** - Spring 项目天然支持 AOP
2. ✅ **零样板代码** - 新增工具自动监控，不会遗漏
3. ✅ **易维护** - 监控逻辑集中管理
4. ✅ **可扩展** - 后续可以轻松添加指标统计、限流等功能

**迁移风险低**：
- 可以先保留 Decorator 代码作为备份
- AOP 如果有问题，立即回退到 Decorator
- 两种方案可以并存（一个关闭即可）

**立即行动**：
1. 创建 `ToolMonitorAspect` 验证可行性（10分钟）
2. 如果可行，逐步迁移（1-2小时）
3. 如果不可行，继续使用 Decorator（已稳定运行）
