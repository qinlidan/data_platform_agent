# 对话持久化统一设计方案

## 问题分析

当前实现的问题：
- 对话持久化逻辑耦合在 `chatStream` 方法中
- 使用 `doOnNext`/`doOnComplete` 钩子只适用于 Flux 流式接口
- 如果添加非流式接口（如 `/api/ai/chat` 返回完整响应），无法复用持久化逻辑

## 设计目标

1. **统一性**: 流式和非流式接口共用同一套持久化逻辑
2. **解耦**: 持久化逻辑与业务逻辑分离
3. **可扩展**: 易于添加新的接口类型
4. **低侵入**: 最小化对现有代码的修改

---

## 方案 1: AOP 切面拦截（推荐）

### 设计思路

使用 Spring AOP 在控制器方法执行前后拦截，自动保存对话消息。

### 核心组件

#### 1. 自定义注解标记需要持久化的方法

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistConversation {
    /**
     * 用户消息参数名
     */
    String userMessageParam() default "message";
    
    /**
     * 用户ID参数名
     */
    String userIdParam() default "userId";
    
    /**
     * 响应类型
     */
    ResponseType responseType() default ResponseType.AUTO_DETECT;
    
    enum ResponseType {
        AUTO_DETECT,  // 自动检测
        BLOCKING,     // 阻塞式（String, Map 等）
        REACTIVE      // 响应式（Flux, Mono）
    }
}
```

#### 2. AOP 切面实现

```java
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ConversationPersistenceAspect {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @Around("@annotation(persistConversation)")
    public Object persistConversation(ProceedingJoinPoint joinPoint, 
                                     PersistConversation persistConversation) throws Throwable {
        
        // 1. 提取参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();
        
        String userId = extractParam(paramNames, paramValues, persistConversation.userIdParam(), "anonymous");
        String userMessage = extractParam(paramNames, paramValues, persistConversation.userMessageParam(), null);
        
        if (userMessage == null) {
            log.warn("未找到用户消息参数，跳过持久化");
            return joinPoint.proceed();
        }
        
        // 2. 保存用户消息
        Long conversationId = conversationService.getOrCreateConversation(userId);
        messageService.saveUserMessage(conversationId, userMessage);
        
        // 3. 执行原方法
        Object result = joinPoint.proceed();
        
        // 4. 根据返回类型保存助手响应
        Class<?> returnType = signature.getReturnType();
        
        if (Flux.class.isAssignableFrom(returnType)) {
            // 流式响应：包装 Flux
            return wrapFluxResponse((Flux<?>) result, conversationId);
        } else if (Mono.class.isAssignableFrom(returnType)) {
            // Mono 响应
            return wrapMonoResponse((Mono<?>) result, conversationId);
        } else {
            // 阻塞式响应：直接保存
            String assistantResponse = extractAssistantResponse(result);
            messageService.saveAssistantMessage(conversationId, assistantResponse, null);
            return result;
        }
    }
    
    /**
     * 包装 Flux 响应，累积内容后保存
     */
    private Flux<?> wrapFluxResponse(Flux<?> flux, Long conversationId) {
        StringBuilder assistantResponse = new StringBuilder();
        
        return flux
            .doOnNext(event -> {
                // 提取消息内容（根据实际 SSE 结构）
                String content = extractContentFromEvent(event);
                if (content != null) {
                    assistantResponse.append(content);
                }
            })
            .doOnComplete(() -> {
                if (assistantResponse.length() > 0) {
                    messageService.saveAssistantMessage(
                        conversationId, 
                        assistantResponse.toString(), 
                        null
                    );
                }
            })
            .doOnError(error -> {
                log.error("流式响应错误", error);
                // 保存错误信息
                if (assistantResponse.length() > 0) {
                    messageService.saveAssistantMessage(
                        conversationId, 
                        assistantResponse.toString() + "\n[错误: " + error.getMessage() + "]", 
                        null
                    );
                }
            });
    }
    
    /**
     * 包装 Mono 响应
     */
    private Mono<?> wrapMonoResponse(Mono<?> mono, Long conversationId) {
        return mono.doOnSuccess(result -> {
            String assistantResponse = extractAssistantResponse(result);
            messageService.saveAssistantMessage(conversationId, assistantResponse, null);
        });
    }
    
    /**
     * 从参数数组中提取指定参数
     */
    private String extractParam(String[] paramNames, Object[] paramValues, String paramName, String defaultValue) {
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(paramName)) {
                return paramValues[i] != null ? paramValues[i].toString() : defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 从 SSE 事件中提取内容
     */
    private String extractContentFromEvent(Object event) {
        if (event instanceof ServerSentEvent) {
            ServerSentEvent<?> sse = (ServerSentEvent<?>) event;
            Object data = sse.data();
            if (data instanceof Map) {
                Map<?, ?> dataMap = (Map<?, ?>) data;
                if ("content".equals(sse.event()) && dataMap.containsKey("content")) {
                    return dataMap.get("content").toString();
                }
            }
        }
        return null;
    }
    
    /**
     * 从阻塞式响应中提取助手响应
     */
    private String extractAssistantResponse(Object result) {
        if (result instanceof String) {
            return (String) result;
        } else if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            return map.getOrDefault("response", result.toString()).toString();
        } else if (result instanceof ApiResponse) {
            ApiResponse<?> apiResponse = (ApiResponse<?>) result;
            return apiResponse.getData() != null ? apiResponse.getData().toString() : "";
        }
        return result != null ? result.toString() : "";
    }
}
```

### 使用示例

#### 流式接口

```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@PersistConversation(userMessageParam = "message", userIdParam = "userId")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    // 业务逻辑无需关心持久化
    return chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse()
            .map(response -> {
                String content = response.getResult().getOutput().getContent();
                return ServerSentEvent.<Map<String, Object>>builder()
                        .event("content")
                        .data(Map.of("content", content))
                        .build();
            });
}
```

#### 非流式接口

```java
@PostMapping("/chat")
@PersistConversation(userMessageParam = "message", userIdParam = "userId")
public ApiResponse<String> chat(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    // 业务逻辑无需关心持久化
    String response = chatClient.prompt()
            .user(message)
            .call()
            .content();
    
    return ApiResponse.success(response);
}
```

### 优点

- ✅ **统一性**: 流式和非流式接口使用同一个切面
- ✅ **无侵入**: 业务代码只需加注解，无需修改内部逻辑
- ✅ **灵活性**: 通过注解参数适配不同的参数名
- ✅ **自动检测**: 根据返回类型自动选择持久化策略

### 缺点

- ⚠️ **复杂性**: 需要处理多种返回类型
- ⚠️ **调试难度**: AOP 增加了调用链复杂度

---

## 方案 2: 服务层封装 + 模板方法模式

### 设计思路

创建一个 `ConversationManager` 服务，封装对话持久化的通用逻辑，使用模板方法模式处理流式和非流式的差异。

### 核心组件

#### ConversationManager

```java
@Service
@RequiredArgsConstructor
public class ConversationManager {

    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 阻塞式对话（适用于传统 REST API）
     */
    public <T> T executeBlocking(
            String userId, 
            String userMessage,
            Function<String, T> aiCall,
            Function<T, String> responseExtractor
    ) {
        // 1. 保存用户消息
        Long conversationId = conversationService.getOrCreateConversation(userId);
        messageService.saveUserMessage(conversationId, userMessage);
        
        try {
            // 2. 调用 AI
            T result = aiCall.apply(userMessage);
            
            // 3. 提取并保存助手响应
            String assistantResponse = responseExtractor.apply(result);
            messageService.saveAssistantMessage(conversationId, assistantResponse, null);
            
            return result;
        } catch (Exception e) {
            // 保存错误信息
            messageService.saveAssistantMessage(conversationId, "[错误: " + e.getMessage() + "]", null);
            throw e;
        }
    }
    
    /**
     * 响应式流对话（适用于 SSE）
     */
    public <T> Flux<T> executeReactive(
            String userId,
            String userMessage,
            Function<String, Flux<T>> aiCall,
            Function<T, String> contentExtractor
    ) {
        // 1. 保存用户消息
        Long conversationId = conversationService.getOrCreateConversation(userId);
        messageService.saveUserMessage(conversationId, userMessage);
        
        // 2. 调用 AI 并包装响应
        StringBuilder assistantResponse = new StringBuilder();
        
        return aiCall.apply(userMessage)
            .doOnNext(event -> {
                String content = contentExtractor.apply(event);
                if (content != null) {
                    assistantResponse.append(content);
                }
            })
            .doOnComplete(() -> {
                if (assistantResponse.length() > 0) {
                    messageService.saveAssistantMessage(
                        conversationId, 
                        assistantResponse.toString(), 
                        null
                    );
                }
            })
            .doOnError(error -> {
                messageService.saveAssistantMessage(
                    conversationId, 
                    assistantResponse.toString() + "\n[错误: " + error.getMessage() + "]", 
                    null
                );
            });
    }
}
```

### 使用示例

#### 流式接口

```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    return conversationManager.executeReactive(
        userId,
        message,
        // AI 调用逻辑
        msg -> chatClient.prompt()
                .user(msg)
                .stream()
                .chatResponse()
                .map(response -> {
                    String content = response.getResult().getOutput().getContent();
                    return ServerSentEvent.<Map<String, Object>>builder()
                            .event("content")
                            .data(Map.of("content", content))
                            .build();
                }),
        // 内容提取逻辑
        event -> {
            if ("content".equals(event.event()) && event.data() instanceof Map) {
                Map<?, ?> data = (Map<?, ?>) event.data();
                return data.get("content").toString();
            }
            return null;
        }
    );
}
```

#### 非流式接口

```java
@PostMapping("/chat")
public ApiResponse<String> chat(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    String response = conversationManager.executeBlocking(
        userId,
        message,
        // AI 调用逻辑
        msg -> chatClient.prompt().user(msg).call().content(),
        // 响应提取逻辑（直接返回）
        result -> result
    );
    
    return ApiResponse.success(response);
}
```

### 优点

- ✅ **显式**: 对话持久化逻辑一目了然
- ✅ **灵活**: 可以完全控制 AI 调用和响应提取逻辑
- ✅ **类型安全**: 编译时检查，不依赖反射

### 缺点

- ⚠️ **侵入性**: 需要修改控制器方法，包装调用逻辑
- ⚠️ **冗余**: 每个接口都需要写包装代码

---

## 方案 3: 装饰器模式 + ChatClient 封装

### 设计思路

创建一个增强版的 `ChatClient`，自动处理对话持久化。

### 核心组件

```java
@Component
@RequiredArgsConstructor
public class PersistentChatClient {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 阻塞式调用
     */
    public String call(String userId, String userMessage) {
        Long conversationId = conversationService.getOrCreateConversation(userId);
        messageService.saveUserMessage(conversationId, userMessage);
        
        String response = chatClientBuilder.build()
                .prompt()
                .user(userMessage)
                .call()
                .content();
        
        messageService.saveAssistantMessage(conversationId, response, null);
        return response;
    }
    
    /**
     * 流式调用
     */
    public Flux<String> stream(String userId, String userMessage) {
        Long conversationId = conversationService.getOrCreateConversation(userId);
        messageService.saveUserMessage(conversationId, userMessage);
        
        StringBuilder assistantResponse = new StringBuilder();
        
        return chatClientBuilder.build()
                .prompt()
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(assistantResponse::append)
                .doOnComplete(() -> {
                    messageService.saveAssistantMessage(
                        conversationId, 
                        assistantResponse.toString(), 
                        null
                    );
                });
    }
}
```

### 使用示例

```java
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    return persistentChatClient.stream(userId, message)
            .map(content -> ServerSentEvent.<Map<String, Object>>builder()
                    .event("content")
                    .data(Map.of("content", content))
                    .build());
}

@PostMapping("/chat")
public ApiResponse<String> chat(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    String response = persistentChatClient.call(userId, message);
    return ApiResponse.success(response);
}
```

### 优点

- ✅ **简洁**: 控制器代码极其简单
- ✅ **复用**: ChatClient 的增强逻辑在所有接口间共享

### 缺点

- ⚠️ **不够灵活**: 难以处理复杂的工具调用和二次确认场景
- ⚠️ **封装过度**: 隐藏了太多细节，不利于调试

---

## 推荐方案

### 🥇 首选: 方案 1（AOP 切面）

**适用场景**:
- 项目有多种类型的 AI 接口（流式、非流式、Mono、Flux）
- 希望业务代码完全解耦持久化逻辑
- 团队熟悉 AOP 编程

**实施步骤**:
1. 创建 `@PersistConversation` 注解
2. 实现 `ConversationPersistenceAspect` 切面
3. 在控制器方法上添加注解
4. 移除现有的 `doOnNext`/`doOnComplete` 持久化代码

### 🥈 备选: 方案 2（服务层封装）

**适用场景**:
- 不想引入 AOP 复杂性
- 需要对持久化逻辑有完全控制
- 接口类型较少（1-2 种）

**实施步骤**:
1. 创建 `ConversationManager` 服务
2. 实现 `executeBlocking` 和 `executeReactive` 方法
3. 重构控制器方法，使用 `ConversationManager` 包装调用

---

## 迁移建议

如果从当前实现迁移到 AOP 方案：

### 迁移前（当前实现）

```java
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    Long conversationId = conversationService.getOrCreateConversation(userId);
    messageService.saveUserMessage(conversationId, message);
    
    StringBuilder assistantResponse = new StringBuilder();
    
    return chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse()
            .map(...)
            .doOnNext(event -> {
                // 累积响应
                assistantResponse.append(...);
            })
            .doOnComplete(() -> {
                // 保存助手响应
                messageService.saveAssistantMessage(...);
            });
}
```

### 迁移后（AOP 方案）

```java
@GetMapping(value = "/chat/stream")
@PersistConversation  // 仅添加注解
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    // 业务逻辑清爽简洁
    return chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse()
            .map(...);
}
```

---

## 总结

| 方案 | 通用性 | 侵入性 | 复杂度 | 推荐度 |
|------|--------|--------|--------|--------|
| AOP 切面 | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐ | 🥇 |
| 服务层封装 | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | 🥈 |
| 装饰器模式 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ | 🥉 |

**核心原则**: 对话持久化是横切关注点（Cross-Cutting Concern），应该与业务逻辑解耦。
