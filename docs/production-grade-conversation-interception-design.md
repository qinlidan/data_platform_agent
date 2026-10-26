# 生产级对话拦截统一设计方案

## 设计目标

基于 Spring Framework 和 Project Reactor 官方最佳实践，实现：

1. ✅ **统一拦截**：流式（Flux/Mono）和非流式接口使用同一套机制
2. ✅ **完整捕获**：用户信息、用户消息、助手响应、工具调用、元数据
3. ✅ **低侵入性**：业务代码只需添加注解
4. ✅ **响应式兼容**：正确处理 Reactor Context 和背压（backpressure）
5. ✅ **可扩展性**：易于添加新的拦截点和元数据提取器

---

## 核心架构

### 技术选型

| 组件 | 技术 | 用途 |
|------|------|------|
| 方法拦截 | Spring AOP `@Around` | 拦截控制器方法，提取参数 |
| 上下文传递 | Reactor Context | 在响应式流中传递 conversationId |
| 响应包装 | `doOnNext`/`doOnComplete` | 累积流式响应内容 |
| 元数据提取 | 策略模式 | 支持不同类型的响应提取 |

### 工作流程图

```
┌─────────────────────────────────────────────────────────────┐
│                    客户端请求                                │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│  ConversationInterceptorAspect (@Around)                    │
│  1. 提取参数：userId, message, metadata                     │
│  2. 保存用户消息到数据库                                     │
│  3. 创建/获取 conversationId                                │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
         ┌────────┴────────┐
         │  返回类型检测    │
         └────────┬────────┘
                  │
      ┌───────────┼───────────┐
      │           │           │
      ▼           ▼           ▼
┌─────────┐ ┌─────────┐ ┌─────────┐
│  Flux   │ │  Mono   │ │ 阻塞式  │
└────┬────┘ └────┬────┘ └────┬────┘
     │           │           │
     │           │           │
     ▼           ▼           ▼
┌─────────────────────────────────┐
│ ResponseWrapper (策略模式)      │
│ - FluxResponseWrapper           │
│ - MonoResponseWrapper           │
│ - BlockingResponseWrapper       │
└─────────┬───────────────────────┘
          │
          ▼
┌─────────────────────────────────┐
│ 1. 累积助手响应内容              │
│ 2. 提取工具调用（如果有）        │
│ 3. 收集元数据                   │
└─────────┬───────────────────────┘
          │
          ▼
┌─────────────────────────────────┐
│ 完成时保存到数据库：              │
│ - 助手消息                       │
│ - 工具调用记录（JSON）           │
│ - 对话元数据                     │
└─────────────────────────────────┘
```

---

## 实现代码

### 1. 自定义注解

```java
package com.zhkj.alert.aspect;

import java.lang.annotation.*;

/**
 * 标记需要拦截和持久化对话的方法
 * 支持流式（Flux/Mono）和非流式接口
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InterceptConversation {
    
    /**
     * 用户消息参数名（默认 "message"）
     */
    String messageParam() default "message";
    
    /**
     * 用户 ID 参数名（默认 "userId"）
     */
    String userIdParam() default "userId";
    
    /**
     * 是否记录工具调用（默认 true）
     */
    boolean recordToolCalls() default true;
    
    /**
     * 响应内容提取器（用于自定义响应解析）
     */
    Class<? extends ResponseExtractor> responseExtractor() default DefaultResponseExtractor.class;
}
```

### 2. 响应提取器接口

```java
package com.zhkj.alert.aspect;

/**
 * 响应内容提取器接口
 * 支持自定义不同格式的响应解析
 */
public interface ResponseExtractor {
    
    /**
     * 从 SSE 事件中提取内容
     */
    String extractFromEvent(Object event);
    
    /**
     * 从阻塞式响应中提取内容
     */
    String extractFromBlocking(Object response);
    
    /**
     * 提取工具调用信息（可选）
     */
    String extractToolCalls(Object data);
}
```

### 3. 默认响应提取器

```java
package com.zhkj.alert.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 默认响应提取器
 * 处理常见的响应格式：ServerSentEvent, Map, String, ApiResponse
 */
@Slf4j
@Component
public class DefaultResponseExtractor implements ResponseExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String extractFromEvent(Object event) {
        if (event instanceof ServerSentEvent) {
            ServerSentEvent<?> sse = (ServerSentEvent<?>) event;
            Object data = sse.data();
            
            // 处理 SSE 事件
            if ("content".equals(sse.event()) && data instanceof Map) {
                Map<?, ?> dataMap = (Map<?, ?>) data;
                return dataMap.getOrDefault("content", "").toString();
            }
            
            if ("tool_call".equals(sse.event()) && data instanceof Map) {
                // 工具调用事件，不累积到内容中
                return null;
            }
        }
        return null;
    }

    @Override
    public String extractFromBlocking(Object response) {
        if (response == null) {
            return "";
        }
        
        if (response instanceof String) {
            return (String) response;
        }
        
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            // 尝试提取常见字段
            for (String key : List.of("response", "content", "message", "data")) {
                if (map.containsKey(key)) {
                    return map.get(key).toString();
                }
            }
            return map.toString();
        }
        
        // 处理自定义 ApiResponse
        if (response.getClass().getSimpleName().equals("ApiResponse")) {
            try {
                // 使用反射提取 data 字段
                var dataField = response.getClass().getDeclaredField("data");
                dataField.setAccessible(true);
                Object data = dataField.get(response);
                return data != null ? data.toString() : "";
            } catch (Exception e) {
                log.warn("无法提取 ApiResponse.data", e);
            }
        }
        
        return response.toString();
    }

    @Override
    public String extractToolCalls(Object data) {
        if (data instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) data;
            if (dataMap.containsKey("tool_calls")) {
                try {
                    return objectMapper.writeValueAsString(dataMap.get("tool_calls"));
                } catch (Exception e) {
                    log.warn("无法序列化工具调用", e);
                }
            }
        }
        return null;
    }
}
```

### 4. 核心 AOP 切面

```java
package com.zhkj.alert.aspect;

import com.zhkj.alert.entity.Conversation;
import com.zhkj.alert.service.ConversationService;
import com.zhkj.alert.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 对话拦截切面
 * 统一处理流式和非流式接口的对话持久化
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ConversationInterceptorAspect {

    private static final String CONVERSATION_ID_KEY = "conversationId";
    private static final String USER_ID_KEY = "userId";

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final Map<Class<? extends ResponseExtractor>, ResponseExtractor> extractorCache = new HashMap<>();

    @Around("@annotation(interceptConversation)")
    public Object interceptConversation(ProceedingJoinPoint joinPoint,
                                       InterceptConversation interceptConversation) throws Throwable {
        
        // 1. 提取方法参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();
        
        String userId = extractParam(paramNames, paramValues, interceptConversation.userIdParam(), "anonymous");
        String userMessage = extractParam(paramNames, paramValues, interceptConversation.messageParam(), null);
        
        if (userMessage == null || userMessage.isBlank()) {
            log.warn("未找到用户消息，跳过对话拦截: method={}", signature.getMethod().getName());
            return joinPoint.proceed();
        }
        
        // 2. 创建或获取对话
        Long conversationId = conversationService.getOrCreateConversation(userId);
        log.debug("对话拦截: userId={}, conversationId={}, message={}", userId, conversationId, userMessage);
        
        // 3. 保存用户消息
        messageService.saveUserMessage(conversationId, userMessage);
        
        // 4. 执行原方法
        Object result = joinPoint.proceed();
        
        // 5. 根据返回类型包装响应
        Class<?> returnType = signature.getReturnType();
        ResponseExtractor extractor = getExtractor(interceptConversation.responseExtractor());
        
        if (Flux.class.isAssignableFrom(returnType)) {
            return wrapFluxResponse((Flux<?>) result, conversationId, userId, extractor, interceptConversation);
        } else if (Mono.class.isAssignableFrom(returnType)) {
            return wrapMonoResponse((Mono<?>) result, conversationId, extractor);
        } else {
            // 阻塞式响应：直接保存
            return handleBlockingResponse(result, conversationId, extractor);
        }
    }

    /**
     * 包装 Flux 响应
     * 使用 Reactor Context 传递 conversationId
     */
    private Flux<?> wrapFluxResponse(Flux<?> flux, Long conversationId, String userId,
                                    ResponseExtractor extractor, InterceptConversation config) {
        
        StringBuilder assistantResponse = new StringBuilder();
        StringBuilder toolCallsJson = new StringBuilder("[");
        boolean[] hasToolCalls = {false};
        
        return flux
            // 注入 conversationId 到 Reactor Context
            .contextWrite(Context.of(
                CONVERSATION_ID_KEY, conversationId,
                USER_ID_KEY, userId
            ))
            .doOnNext(event -> {
                // 提取内容
                String content = extractor.extractFromEvent(event);
                if (content != null && !content.isEmpty()) {
                    assistantResponse.append(content);
                }
                
                // 提取工具调用
                if (config.recordToolCalls()) {
                    String toolCall = extractor.extractToolCalls(event);
                    if (toolCall != null) {
                        if (hasToolCalls[0]) {
                            toolCallsJson.append(",");
                        }
                        toolCallsJson.append(toolCall);
                        hasToolCalls[0] = true;
                    }
                }
            })
            .doOnComplete(() -> {
                // 完成时保存助手响应
                toolCallsJson.append("]");
                String toolCallsFinal = hasToolCalls[0] ? toolCallsJson.toString() : null;
                
                if (assistantResponse.length() > 0 || toolCallsFinal != null) {
                    messageService.saveAssistantMessage(
                        conversationId,
                        assistantResponse.toString(),
                        toolCallsFinal
                    );
                    log.debug("已保存助手响应: conversationId={}, length={}, toolCalls={}",
                        conversationId, assistantResponse.length(), toolCallsFinal != null);
                }
            })
            .doOnError(error -> {
                // 错误时也保存已累积的内容
                log.error("流式响应错误: conversationId={}", conversationId, error);
                if (assistantResponse.length() > 0) {
                    messageService.saveAssistantMessage(
                        conversationId,
                        assistantResponse + "\n[错误: " + error.getMessage() + "]",
                        null
                    );
                }
            });
    }

    /**
     * 包装 Mono 响应
     */
    private Mono<?> wrapMonoResponse(Mono<?> mono, Long conversationId, ResponseExtractor extractor) {
        return mono.doOnSuccess(result -> {
            if (result != null) {
                String assistantResponse = extractor.extractFromBlocking(result);
                String toolCalls = extractor.extractToolCalls(result);
                messageService.saveAssistantMessage(conversationId, assistantResponse, toolCalls);
            }
        });
    }

    /**
     * 处理阻塞式响应
     */
    private Object handleBlockingResponse(Object result, Long conversationId, ResponseExtractor extractor) {
        String assistantResponse = extractor.extractFromBlocking(result);
        String toolCalls = extractor.extractToolCalls(result);
        messageService.saveAssistantMessage(conversationId, assistantResponse, toolCalls);
        return result;
    }

    /**
     * 从参数数组中提取指定参数
     */
    private String extractParam(String[] paramNames, Object[] paramValues, String targetName, String defaultValue) {
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(targetName) && paramValues[i] != null) {
                return paramValues[i].toString();
            }
        }
        return defaultValue;
    }

    /**
     * 获取响应提取器实例（带缓存）
     */
    private ResponseExtractor getExtractor(Class<? extends ResponseExtractor> extractorClass) {
        return extractorCache.computeIfAbsent(extractorClass, clazz -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.warn("无法实例化提取器 {}, 使用默认实现", clazz.getName(), e);
                return new DefaultResponseExtractor();
            }
        });
    }
}
```

---

## 使用示例

### 示例 1: 流式 AI 对话接口

```java
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * 流式 AI 对话（SSE）
     * 自动拦截并保存对话
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @InterceptConversation  // ✅ 只需添加注解
    public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "anonymous") String userId
    ) {
        // 业务逻辑完全不关心持久化
        return chatClientBuilder.build()
                .prompt()
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
}
```

### 示例 2: 非流式 AI 对话接口

```java
/**
 * 非流式 AI 对话（JSON 响应）
 * 使用同一个注解，自动适配
 */
@PostMapping("/chat")
@InterceptConversation  // ✅ 同样只需添加注解
public ApiResponse<String> chat(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    // 调用 AI，返回完整响应
    String response = chatClientBuilder.build()
            .prompt()
            .user(message)
            .call()
            .content();
    
    return ApiResponse.success(response);
}
```

### 示例 3: 带工具调用的复杂对话

```java
/**
 * 带工具调用的 AI 对话
 * 自动记录工具调用详情
 */
@GetMapping(value = "/chat/tools/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@InterceptConversation(recordToolCalls = true)  // ✅ 启用工具调用记录
public Flux<ServerSentEvent<Map<String, Object>>> chatWithTools(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    return chatClientBuilder.build()
            .prompt()
            .user(message)
            .stream()
            .chatResponse()
            .flatMapSequential(response -> {
                // 处理内容和工具调用
                List<ServerSentEvent<Map<String, Object>>> events = new ArrayList<>();
                
                // 内容事件
                String content = response.getResult().getOutput().getContent();
                if (content != null && !content.isEmpty()) {
                    events.add(ServerSentEvent.<Map<String, Object>>builder()
                            .event("content")
                            .data(Map.of("content", content))
                            .build());
                }
                
                // 工具调用事件
                var toolCalls = response.getResult().getOutput().getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    events.add(ServerSentEvent.<Map<String, Object>>builder()
                            .event("tool_call")
                            .data(Map.of(
                                "tool_calls", toolCalls.stream()
                                    .map(tc -> Map.of(
                                        "name", tc.name(),
                                        "arguments", tc.arguments()
                                    ))
                                    .toList()
                            ))
                            .build());
                }
                
                return Flux.fromIterable(events);
            });
}
```

### 示例 4: 自定义响应提取器

```java
/**
 * 自定义响应提取器
 * 处理特殊格式的响应
 */
public class CustomResponseExtractor implements ResponseExtractor {
    
    @Override
    public String extractFromEvent(Object event) {
        // 自定义提取逻辑
        if (event instanceof MyCustomEvent) {
            MyCustomEvent custom = (MyCustomEvent) event;
            return custom.getContent();
        }
        return null;
    }
    
    @Override
    public String extractFromBlocking(Object response) {
        // 自定义阻塞式响应提取
        if (response instanceof MyCustomResponse) {
            return ((MyCustomResponse) response).getText();
        }
        return response.toString();
    }
    
    @Override
    public String extractToolCalls(Object data) {
        // 自定义工具调用提取
        return null;
    }
}

// 使用自定义提取器
@GetMapping("/custom/chat")
@InterceptConversation(responseExtractor = CustomResponseExtractor.class)
public Flux<MyCustomEvent> customChat(
        @RequestParam String message,
        @RequestParam String userId
) {
    // ...
}
```

---

## 高级特性

### 1. 在业务代码中访问 Reactor Context

```java
@GetMapping("/chat/context-aware")
@InterceptConversation
public Flux<ServerSentEvent<Map<String, Object>>> contextAwareChat(
        @RequestParam String message,
        @RequestParam String userId
) {
    return chatClientBuilder.build()
            .prompt()
            .user(message)
            .stream()
            .chatResponse()
            .flatMap(response -> 
                // 从 Context 中读取 conversationId
                Mono.deferContextual(ctx -> {
                    Long conversationId = ctx.get("conversationId");
                    String ctxUserId = ctx.get("userId");
                    
                    // 可以在流中使用这些信息做其他操作
                    log.info("处理中: conversationId={}, userId={}", conversationId, ctxUserId);
                    
                    return Mono.just(ServerSentEvent.<Map<String, Object>>builder()
                            .event("content")
                            .data(Map.of("content", response.getResult().getOutput().getContent()))
                            .build());
                })
            );
}
```

### 2. 条件拦截

```java
/**
 * 仅在特定条件下拦截
 */
@GetMapping("/chat/conditional")
@InterceptConversation
public Flux<ServerSentEvent<Map<String, Object>>> conditionalChat(
        @RequestParam String message,
        @RequestParam String userId,
        @RequestParam(required = false, defaultValue = "true") boolean persist
) {
    // 如果不需要持久化，可以在参数中传递 persist=false
    // 切面可以检查这个参数决定是否跳过
    // ...
}
```

### 3. 元数据注入

```java
/**
 * 在助手响应中注入对话元数据
 */
@GetMapping("/chat/with-metadata")
@InterceptConversation
public Flux<ServerSentEvent<Map<String, Object>>> chatWithMetadata(
        @RequestParam String message,
        @RequestParam String userId
) {
    return chatClientBuilder.build()
            .prompt()
            .user(message)
            .stream()
            .chatResponse()
            .concatWith(
                // 流结束时，发送元数据事件
                Mono.deferContextual(ctx -> {
                    Long conversationId = ctx.get("conversationId");
                    return Mono.just(ServerSentEvent.<Map<String, Object>>builder()
                            .event("metadata")
                            .data(Map.of(
                                "conversationId", conversationId,
                                "timestamp", System.currentTimeMillis()
                            ))
                            .build());
                })
            )
            .map(response -> {
                // 处理响应
                return ServerSentEvent.<Map<String, Object>>builder()
                        .event("content")
                        .data(Map.of("content", response.getResult().getOutput().getContent()))
                        .build();
            });
}
```

---

## 测试策略

### 单元测试：Mock AOP 切面

```java
@SpringBootTest
@AutoConfigureMockMvc
class ConversationInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private MessageService messageService;

    @Test
    void testBlockingChatInterception() throws Exception {
        // Given
        when(conversationService.getOrCreateConversation("testUser")).thenReturn(1L);
        
        // When & Then
        mockMvc.perform(post("/api/ai/chat")
                .param("message", "Hello AI")
                .param("userId", "testUser"))
                .andExpect(status().isOk());
        
        // Verify
        verify(messageService).saveUserMessage(eq(1L), eq("Hello AI"));
        verify(messageService).saveAssistantMessage(eq(1L), anyString(), isNull());
    }

    @Test
    void testStreamingChatInterception() {
        // 使用 StepVerifier 测试 Flux
        // ...
    }
}
```

---

## 性能优化建议

### 1. 异步持久化

```java
// 使用 Spring 的 @Async 异步保存消息
@Async
public void saveUserMessageAsync(Long conversationId, String content) {
    messageService.saveUserMessage(conversationId, content);
}
```

### 2. 批量保存

```java
// 对于高并发场景，使用批量插入
private final List<Message> messageBatch = new CopyOnWriteArrayList<>();

@Scheduled(fixedRate = 1000)  // 每秒批量保存一次
public void flushMessages() {
    if (!messageBatch.isEmpty()) {
        messageService.batchInsert(messageBatch);
        messageBatch.clear();
    }
}
```

### 3. 缓存 ConversationId

```java
// 使用 Caffeine 缓存避免频繁查询数据库
@Cacheable(value = "conversations", key = "#userId")
public Long getOrCreateConversation(String userId) {
    // ...
}
```

---

## 对比当前实现

| 维度 | 当前实现（`doOnNext` 钩子） | 新方案（AOP + Context） |
|------|---------------------------|------------------------|
| 通用性 | ⭐⭐ 仅支持 Flux | ⭐⭐⭐⭐⭐ 支持 Flux/Mono/阻塞式 |
| 代码侵入 | ⭐⭐ 每个接口都要写 | ⭐⭐⭐⭐⭐ 只需加注解 |
| 可维护性 | ⭐⭐⭐ 持久化逻辑分散 | ⭐⭐⭐⭐⭐ 集中在切面 |
| 扩展性 | ⭐⭐ 难以添加新功能 | ⭐⭐⭐⭐ 策略模式易扩展 |
| 工具调用支持 | ⭐⭐ 需要额外代码 | ⭐⭐⭐⭐⭐ 自动提取和记录 |
| Reactor 兼容性 | ⭐⭐⭐⭐ 使用正确 | ⭐⭐⭐⭐⭐ Context 传递 |

---

## 迁移步骤

### Step 1: 添加切面相关类

```bash
# 创建包
mkdir -p src/main/java/com/zhkj/alert/aspect

# 创建文件
touch src/main/java/com/zhkj/alert/aspect/InterceptConversation.java
touch src/main/java/com/zhkj/alert/aspect/ResponseExtractor.java
touch src/main/java/com/zhkj/alert/aspect/DefaultResponseExtractor.java
touch src/main/java/com/zhkj/alert/aspect/ConversationInterceptorAspect.java
```

### Step 2: 重构现有控制器

```java
// Before
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(...) {
    Long conversationId = conversationService.getOrCreateConversation(userId);
    messageService.saveUserMessage(conversationId, message);
    StringBuilder assistantResponse = new StringBuilder();
    
    return chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse()
            .map(...)
            .doOnNext(event -> assistantResponse.append(...))
            .doOnComplete(() -> messageService.saveAssistantMessage(...));
}

// After
@GetMapping(value = "/chat/stream")
@InterceptConversation  // ✅ 仅添加注解
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(...) {
    return chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse()
            .map(...);  // 清爽简洁
}
```

### Step 3: 测试验证

```bash
# 运行测试
./mvnw test -Dtest=ConversationInterceptorTest

# 验证数据库
# 1. 发送几条测试消息
# 2. 检查 messages 表是否正确记录
# 3. 检查 tool_calls 字段是否有 JSON 数据
```

---

## 总结

### 核心优势

1. ✅ **统一拦截机制**：流式和非流式接口使用同一个 `@InterceptConversation` 注解
2. ✅ **完整数据捕获**：用户消息、助手响应、工具调用、元数据全部自动记录
3. ✅ **Reactor 最佳实践**：使用 Context 传递元数据，符合响应式编程规范
4. ✅ **低侵入性**：业务代码只需加注解，持久化逻辑完全解耦
5. ✅ **高扩展性**：策略模式支持自定义响应提取器

### 关键技术点

| 技术 | 应用场景 | 文档来源 |
|------|---------|----------|
| Spring AOP `@Around` | 方法拦截、参数提取 | Spring Framework Reference |
| Reactor Context | 响应式流中传递元数据 | Project Reactor Reference |
| `doOnNext`/`doOnComplete` | 流式内容累积 | Reactor Core API |
| 策略模式 | 支持不同响应格式 | 设计模式 |

### 最佳实践

- ✅ 对话持久化是**横切关注点（Cross-Cutting Concern）**，应使用 AOP
- ✅ 响应式流中传递元数据使用 **Reactor Context**，不要用 ThreadLocal
- ✅ 使用 **策略模式** 支持不同类型的响应提取
- ✅ 异步/批量保存提升高并发场景性能
