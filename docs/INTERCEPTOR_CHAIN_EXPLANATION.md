# 拦截器链使用说明

## 整体架构

拦截器链采用**责任链模式（Chain of Responsibility Pattern）**，通过多层拦截器对工具调用进行治理。

## 核心组件及其关系

### 1. InterceptorChainConfig（配置类）

**位置**: `com.dataplatform.ai.config.InterceptorChainConfig`

**作用**: 定义拦截器的执行顺序和装配规则

**核心代码**:
```java
@Configuration
@RequiredArgsConstructor
public class InterceptorChainConfig {
    
    @Bean
    public List<ToolInterceptor> toolInterceptors() {
        return Arrays.asList(
            auditInterceptor,           // 1. 审计（记录所有状态）
            permissionInterceptor,      // 2. 权限校验
            validationInterceptor,      // 3. 参数校验
            confirmationInterceptor,    // 4. 二次确认
            timeoutRetryInterceptor     // 5. 超时重试
        );
    }
}
```

**关键点**: 
- 使用 `@Bean` 注解，将拦截器列表注册为 Spring Bean
- 列表顺序即为执行顺序（从外到内）
- Spring 会自动注入所有拦截器实例

### 2. ToolExecutor（执行器）

**位置**: `com.dataplatform.ai.executor.ToolExecutor`

**作用**: 构建拦截器链并执行工具调用

**核心代码**:
```java
@Component
@RequiredArgsConstructor
public class ToolExecutor {
    
    private final List<ToolInterceptor> interceptors;  // ← Spring 自动注入上面的 toolInterceptors Bean
    
    public String execute(ToolExecutionContext context,
                          Function<ToolExecutionContext, String> actualToolCall) throws Exception {
        // 构建拦截器链
        InterceptorChain chain = buildChain(0, actualToolCall);
        
        // 执行拦截器链
        return chain.proceed(context);
    }
    
    private InterceptorChain buildChain(int index, Function<ToolExecutionContext, String> actualToolCall) {
        if (index >= interceptors.size()) {
            // 所有拦截器都执行完毕，调用实际工具
            return context -> actualToolCall.apply(context);
        }
        
        // 递归构建下一个拦截器
        ToolInterceptor currentInterceptor = interceptors.get(index);
        InterceptorChain nextChain = buildChain(index + 1, actualToolCall);
        
        return context -> currentInterceptor.intercept(context, nextChain);
    }
}
```

**关键点**:
- 通过 `@RequiredArgsConstructor` 和构造器注入获取拦截器列表
- Spring 会自动将 `InterceptorChainConfig` 中定义的 `toolInterceptors` Bean 注入到这里
- 使用递归方式构建责任链

### 3. ObservableToolCallback（工具回调包装器）

**位置**: `com.dataplatform.ai.ObservableToolCallback`

**作用**: 包装原始的工具调用，添加拦截器处理

**核心代码**:
```java
public class ObservableToolCallback implements ToolCallback {
    
    private final ToolCallback delegate;
    private final Sinks.Many<Map<String, Object>> eventSink;
    private final ToolExecutor toolExecutor;  // ← 注入的 ToolExecutor
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 构建执行上下文
        ToolExecutionContext context = ToolExecutionContext.builder()
            .toolName(delegate.getName())
            .parameters(parseToolInput(toolInput))
            .userId(extractUserId(toolContext))
            .build();
        
        try {
            // 通过 ToolExecutor 执行，经过拦截器链
            String result = toolExecutor.execute(context, ctx -> {
                // 这里是实际的工具调用
                return delegate.call(toolInput, toolContext);
            });
            
            return result;
        } catch (Exception e) {
            // 异常处理
            throw new RuntimeException("Tool execution failed", e);
        }
    }
}
```

### 4. AiAssistantController（控制器）

**位置**: `com.dataplatform.controller.AiAssistantController`

**作用**: 处理用户请求，组装工具和拦截器

**核心代码**:
```java
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {
    
    private final ToolExecutor toolExecutor;  // ← 注入的 ToolExecutor
    
    @PostMapping("/chat-stream")
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        
        // 包装工具，添加拦截器处理
        ToolCallback[] tools = ToolMonitorConfig.wrapTools(
            eventSink,
            toolExecutor,  // ← 传递给 ObservableToolCallback
            dataSourceTool,
            faqTool,
            jobTool
        );
        
        // 使用包装后的工具调用 AI
        return chatModel.stream(prompt, tools);
    }
}
```

## 完整的调用流程

```
用户发起请求
    ↓
AiAssistantController.chatStream()
    ↓
使用 ToolMonitorConfig.wrapTools() 包装工具
    ↓
创建 ObservableToolCallback（包含 ToolExecutor）
    ↓
AI 模型决定调用工具
    ↓
ObservableToolCallback.call()
    ↓
ToolExecutor.execute()
    ↓
buildChain() 构建拦截器链：
    - AuditInterceptor（审计）
    - PermissionInterceptor（权限校验）
    - ValidationInterceptor（参数校验）
    - ConfirmationInterceptor（二次确认）
    - TimeoutRetryInterceptor（超时重试）
    ↓
InterceptorChain.proceed() 依次执行拦截器
    ↓
最终调用实际工具（如 DataSourceTool.queryData()）
    ↓
结果逐层返回
    ↓
返回给用户
```

## Spring 依赖注入关系

```
InterceptorChainConfig
    ├─ @Bean List<ToolInterceptor> toolInterceptors()
    │   └─ 返回拦截器列表
    │
    ↓ Spring 自动注入
    
ToolExecutor
    ├─ @Component
    ├─ private final List<ToolInterceptor> interceptors;
    │   └─ 由 Spring 注入 toolInterceptors Bean
    │
    ↓ Spring 自动注入
    
AiAssistantController
    ├─ @RestController
    └─ private final ToolExecutor toolExecutor;
        └─ 由 Spring 注入 ToolExecutor Bean
```

## 关键设计点

### 1. 为什么看不到 InterceptorChainConfig 的使用？

因为 Spring 的依赖注入是**隐式的**：

- `InterceptorChainConfig` 用 `@Configuration` 标注，Spring 会扫描到它
- 方法 `toolInterceptors()` 用 `@Bean` 标注，返回值被注册为 Bean
- `ToolExecutor` 需要 `List<ToolInterceptor>` 类型的依赖
- Spring 自动匹配类型，将 `toolInterceptors()` 返回的列表注入

### 2. 为什么 ToolExecutor 能拿到拦截器？

通过 **构造器注入**（`@RequiredArgsConstructor` 生成的构造器）：

```java
@Component
@RequiredArgsConstructor
public class ToolExecutor {
    private final List<ToolInterceptor> interceptors;  // final 字段
    
    // Lombok 生成的构造器：
    // public ToolExecutor(List<ToolInterceptor> interceptors) {
    //     this.interceptors = interceptors;
    // }
}
```

Spring 看到构造器参数 `List<ToolInterceptor>`，就会查找并注入对应的 Bean。

### 3. 递归构建责任链的原理

```java
private InterceptorChain buildChain(int index, Function actualToolCall) {
    if (index >= interceptors.size()) {
        // 终止条件：返回实际工具调用
        return context -> actualToolCall.apply(context);
    }
    
    // 递归：当前拦截器 + 下一层链
    ToolInterceptor current = interceptors.get(index);
    InterceptorChain next = buildChain(index + 1, actualToolCall);
    
    return context -> current.intercept(context, next);
}
```

**执行顺序**:
1. 第一次调用 `buildChain(0, toolCall)`
2. 返回 `context -> auditInterceptor.intercept(context, chain1)`
3. `chain1` 是 `buildChain(1, toolCall)` 的结果
4. `chain1` 返回 `context -> permissionInterceptor.intercept(context, chain2)`
5. 依此类推，最后一个链是实际工具调用

## 使用示例

### 添加新的拦截器

1. 实现 `ToolInterceptor` 接口：
```java
@Component
public class CustomInterceptor implements ToolInterceptor {
    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
        // 前置处理
        System.out.println("Before tool call");
        
        // 调用下一个拦截器
        String result = chain.proceed(context);
        
        // 后置处理
        System.out.println("After tool call");
        
        return result;
    }
}
```

2. 在 `InterceptorChainConfig` 中注册：
```java
@Bean
public List<ToolInterceptor> toolInterceptors() {
    return Arrays.asList(
        auditInterceptor,
        permissionInterceptor,
        customInterceptor,  // ← 添加新拦截器
        validationInterceptor,
        confirmationInterceptor,
        timeoutRetryInterceptor
    );
}
```

3. Spring 会自动重新注入，无需修改其他代码！

## 总结

`InterceptorChainConfig` 虽然没有被显式调用，但它通过 Spring 的依赖注入机制，将拦截器列表提供给 `ToolExecutor`，从而完成了整个拦截器链的装配。这是 Spring 框架的核心特性之一：**依赖反转（IoC）和依赖注入（DI）**。
