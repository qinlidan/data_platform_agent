# Spring AI 异步落表实现方案

## 技术方案

基于 **Spring Event + @Async** 实现 AI 对话消息的异步落表，统一处理同步接口和流式接口。

## 核心组件

### 1. MessageSaveEvent（事件）
```
com.dataplatform.ai.event.MessageSaveEvent
```

消息保存事件，封装需要落表的消息数据：
- conversationId：会话 ID
- role：消息角色（user/assistant/tool）
- content：消息内容
- toolCalls：工具调用信息（JSON 字符串，可选）
- toolCallId：工具调用 ID（可选）

### 2. MessageEventListener（监听器）
```
com.dataplatform.ai.event.MessageEventListener
```

异步处理消息落表：
- 使用 `@Async` 注解实现异步执行
- 自动将 JSON 字符串转换为 List<ToolCall>
- 容错处理，落表失败只记录日志不影响主流程

### 3. AsyncConfig（线程池配置）
```
com.dataplatform.config.AsyncConfig
```

配置异步任务线程池：
- 核心线程数：4
- 最大线程数：8
- 队列容量：100
- 优雅关闭：等待任务完成

## 落表时机

### 用户消息
```java
// 收到请求立即发事件
eventPublisher.publishEvent(new MessageSaveEvent(this, conversationId, "user", message));
```

### 助手消息（流式）
```java
// 流结束后，累积的完整内容发事件
.doOnComplete(() -> {
    if (assistantResponse.length() > 0) {
        eventPublisher.publishEvent(new MessageSaveEvent(
            this, conversationId, "assistant", assistantResponse.toString()
        ));
    }
})
```

### 工具结果
```java
// 工具执行完成后发事件
if ("tool_end".equals(eventName)) {
    eventPublisher.publishEvent(new MessageSaveEvent(
        this, conversationId, "tool", toolResult, null, toolCallId
    ));
}
```

## 执行流程

```
用户请求
   ↓
Controller 接收
   ↓
发布 MessageSaveEvent（用户消息）
   ↓
开始流式响应（不等待落表）
   ↓
逐 token 累积助手回答
   ↓
工具调用 → 发布 MessageSaveEvent（工具结果）
   ↓
流结束 → 发布 MessageSaveEvent（助手消息）
   ↓
返回前端

// 后台异步执行
MessageEventListener.handleMessageSave()
   ↓
转换数据类型
   ↓
messageService.save()
   ↓
数据库落表
```

## 优势

1. **不阻塞主流程**：落表操作在独立线程池执行，不影响流式响应速度
2. **统一代码路径**：同步接口和流式接口都通过事件发布，复用同一套落表逻辑
3. **容错性强**：落表失败不会导致请求失败，只记录日志
4. **易于扩展**：需要添加其他异步操作（如审计日志、通知）只需增加 EventListener

## 配置要求

### 启用异步支持
```java
@SpringBootApplication
@EnableAsync  // 必须添加
public class DataPlatformApplication {
    // ...
}
```

### 依赖注入
Controller 需要注入 `ApplicationEventPublisher`：
```java
private final ApplicationEventPublisher eventPublisher;
```

## 注意事项

1. **助手消息完整性**：必须在流结束后（doOnComplete）才发事件，确保保存的是完整回答
2. **toolCalls 格式**：Event 中使用 JSON 字符串，Listener 负责转换为 List<ToolCall>
3. **线程安全**：StringBuilder 在单个流中是线程安全的（Reactor 保证）
4. **异常处理**：Listener 中捕获所有异常，防止影响其他消息的落表

## 测试建议

1. **流式接口**：观察日志，确认消息按顺序落表（用户 → 助手 → 工具）
2. **并发场景**：多个用户同时对话，验证线程池和数据库操作的正确性
3. **异常场景**：模拟数据库连接失败，确认主流程不受影响

## 文件清单

- `com.dataplatform.ai.event.MessageSaveEvent`
- `com.dataplatform.ai.event.MessageEventListener`
- `com.dataplatform.config.AsyncConfig`
- `com.dataplatform.controller.AiAssistantController`（已修改）
- `com.dataplatform.controller.ConversationController`（新增）
- `com.dataplatform.DataPlatformApplication`（已添加 @EnableAsync）
- `src/main/resources/static/ai-chat.html`（演示页面）

## 实现细节

### 1. 事件驱动架构

**MessageSaveEvent** - 消息保存事件
```java
public class MessageSaveEvent extends ApplicationEvent {
    private final Long conversationId;
    private final String role;
    private final String content;
}
```

**MessageEventListener** - 异步事件监听器
```java
@Component
@Slf4j
public class MessageEventListener {
    
    private final MessageService messageService;
    
    @Async("messageExecutor")
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageSaveEvent(MessageSaveEvent event) {
        // 异步落表
        messageService.saveMessage(
            event.getConversationId(),
            event.getRole(),
            event.getContent()
        );
    }
}
```

### 2. 线程池配置

**AsyncConfig** - 独立线程池
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean("messageExecutor")
    public Executor messageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("msg-save-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 3. Controller 改造

#### 3.1 流式对话接口改造（/chat/stream）

**关键点：**
- 用户消息：立即发事件
- 助手消息：在 `doOnComplete()` 中发事件（流结束后）
- 工具调用：在 `tool_end` 时发事件

**代码示例：**
```java
// 1. 用户消息：立即异步落表
eventPublisher.publishEvent(new MessageSaveEvent(this, convId, "user", message));

// 2. 助手消息：收集完整内容后异步落表
StringBuilder assistantResponse = new StringBuilder();
Flux<ServerSentEvent<Map<String, Object>>> textStream = contentStream
    .doOnNext(content -> assistantResponse.append(content))
    .doOnComplete(() -> {
        if (assistantResponse.length() > 0) {
            eventPublisher.publishEvent(new MessageSaveEvent(
                this, convId, "assistant", assistantResponse.toString()
            ));
        }
    });

// 3. 工具调用：tool_end 时异步落表
.doOnNext(toolCall -> {
    if ("tool_end".equals(toolCall.event())) {
        eventPublisher.publishEvent(new MessageSaveEvent(
            this, convId, "tool", toolCall.data().toString()
        ));
    }
});
```

#### 3.2 纯对话接口改造（/chat/pure/stream）

同样的模式：
```java
// 用户消息
eventPublisher.publishEvent(new MessageSaveEvent(this, convId, "user", message));

// 助手消息（流结束后）
StringBuilder assistantResponse = new StringBuilder();
Flux<ServerSentEvent<Map<String, Object>>> textStream = contentStream
    .doOnNext(content -> assistantResponse.append(content))
    .doOnComplete(() -> {
        if (assistantResponse.length() > 0) {
            eventPublisher.publishEvent(new MessageSaveEvent(
                this, convId, "assistant", assistantResponse.toString()
            ));
        }
    });
```

#### 3.3 同步对话接口改造（/chat）

```java
// 创建或获取会话
Long conversationId = conversationService.getOrCreateConversation(userId, null);

// 用户消息
eventPublisher.publishEvent(new MessageSaveEvent(this, conversationId, "user", userMessage));

// 调用 AI
String response = client.prompt()...

// 助手消息
eventPublisher.publishEvent(new MessageSaveEvent(this, conversationId, "assistant", response));
```

### 4. 会话管理 API

新增 **ConversationController**：

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/conversations` | 创建新会话 |
| GET | `/api/conversations` | 获取用户会话列表 |
| GET | `/api/conversations/{id}` | 获取会话详情 |
| GET | `/api/conversations/{id}/messages` | 获取会话消息历史 |
| PATCH | `/api/conversations/{id}/title` | 更新会话标题 |
| DELETE | `/api/conversations/{id}` | 软删除会话 |
| POST | `/api/conversations/{id}/close` | 结束会话 |

### 5. 前端演示页面

创建了 `/static/ai-chat.html`：
- 自动创建会话
- 实时流式显示
- 显示工具调用过程
- 打字动画效果
