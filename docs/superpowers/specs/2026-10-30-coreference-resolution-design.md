# 智能助手指代消解设计文档

## 意图与背景

### 问题
用户在多轮对话中经常使用指代词（"刚那个作业"、"它"、"那个数据源"）或口语化表达（"再查一遍"、"帮我看看"），导致：
1. 单独看某一句话无法理解用户意图
2. 依赖对话历史才能明确具体实体（作业ID、数据源ID等）
3. 如果 AI 理解不了指代，会要求用户重新说清楚，体验差

### 目标
让 AI 助手能够理解带指代的问句，自动补全成完整、明确的问句，无需用户重复说明。

**验收用例：**
```
用户: 查询作业ID为3的作业
AI: [返回作业3的详情]
用户: 刚那个作业再查一遍
AI: [理解为"查询作业ID为3的详情"，返回作业3的详情]
```

## Goals / Non-Goals / Anti-Goals

### Goals
1. **指代消解准确**：能从对话历史中提取实体，补全指代词（"刚那个作业" → "作业ID为3"）
2. **不强行改写**：问题本身已完整、无指代的，原样透传，不做多余改写
3. **口语化归一**：把模糊口语问法归一成标准问法（"再查一遍" → "查询详情"）
4. **延用现有组件**：不引入新的存储、队列、微服务

### Non-Goals
1. **跨会话记忆**：不需要记住上次会话的内容，只在当前会话内消解
2. **微调模型**：不做 BERT 意图分类、不训练专用模型

### Anti-Goals
1. **过度改写**：把用户原本清晰的问句改得面目全非
2. **慢响应**：指代消解不能拖慢整体响应速度（目标 <500ms）

## 约束

### 技术约束
- **技术栈**：Spring AI 1.0.0 + DeepSeek-V3（主模型）+ ChatMemory
- **不新增组件**：延用现有 ChatModel、ChatMemory、Redis
- **API 查证**：所有 Spring AI API 用法必须先查 Context7 MCP 官方文档

### 业务约束
- **会话隔离**：指代消解只能访问当前会话历史，不能跨会话
- **存储策略**：ChatMemory 存储用户原始消息，不存改写结果

## 技术方案

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    AiAssistantController                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────┐
              │  1. 用户输入              │
              │  "刚那个作业再查一遍"      │
              └───────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│          CoreferenceResolutionService                        │
│  ┌────────────────────────────────────────────────────┐    │
│  │  指代消解 ChatClient                                │    │
│  │  - 共用 ChatMemory (conversationId 相同)           │    │
│  │  - 无工具，只做文本改写                             │    │
│  │  - System Prompt: "根据对话历史补全指代"           │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────┐
              │  2. 改写后消息             │
              │  "查询作业ID为3的详情"     │
              └───────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    主 ChatClient                             │
│  - 工具调用 (JobTool, DataSourceTool, FaqTool)             │
│  - MessageChatMemoryAdvisor                                 │
│  - 存储：原始消息 + AI 响应                                 │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. CoreferenceResolutionService

**职责**：接收用户原始消息 + conversationId，输出改写后的完整问句

**实现要点**：
- 注入 `ChatModel` 和 `ChatMemory`
- 创建一个独立的 `ChatClient`，配置：
  - `MessageChatMemoryAdvisor`（与主流程共用 ChatMemory）
  - 专用 System Prompt（见下文）
  - 无工具回调
- 方法签名：
  ```java
  public String resolveAndRewrite(String userMessage, String conversationId)
  ```
- 调用 `chatClient.prompt().user(userMessage).advisors(conversationId).call().content()`
- 返回改写后的问句（纯文本）

**System Prompt**：
```
你是一个对话指代消解助手。

任务：根据对话历史，把用户当前输入补全成一句不依赖上下文也能看懂的完整问句。

规则：
1. 如果用户消息包含指代词（刚才、那个、它、这个作业、那个数据源等），从历史中提取具体实体（ID、名称），补全到问句中。
   - 例：用户上一轮提到"作业ID为3"，这一轮说"刚那个作业再查一遍" → 改写为"查询作业ID为3的详情"
2. 如果用户消息本身已完整、明确，原样返回，不要强行改写。
   - 例："查询作业ID为5的详情" → 原样返回
3. 如果用户消息是口语化模糊表达（"再查一遍"、"帮我看看"），结合历史归一成标准问法。
   - 例：上一轮在查作业3，这一轮说"再查一遍" → "查询作业ID为3的详情"
4. 只输出改写后的问句，不要解释、不要加前缀（如"改写为："），不要输出其他内容。

示例：
[历史] 用户: 查询作业ID为3的作业
[历史] 助手: 作业ID为3，名称为xxx，状态为运行中...
[当前] 用户: 刚那个作业再查一遍
[输出] 查询作业ID为3的详情

[当前] 用户: 查询作业ID为5的详情
[输出] 查询作业ID为5的详情
```

#### 2. AiAssistantController 改造

在 `chatStream()` 方法中，调用主 ChatClient 之前，先调用指代消解：

**改造点（第 207 行附近）**：
```java
// 【新增】指代消解
String resolvedMessage = coreferenceResolutionService.resolveAndRewrite(
    message, 
    String.valueOf(finalConversationId)
);
log.info("指代消解：原始='{}', 改写='{}'", message, resolvedMessage);

// 文本流：使用改写后的消息
Flux<ServerSentEvent<Map<String, Object>>> textStream = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(resolvedMessage)  // ← 使用改写后的消息
        .toolContext(buildToolContext(userId, confirmationToken))
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(finalConversationId)))
        .stream()
        // ...
```

**注意**：
- 异步落表时，仍然存储用户原始消息 `message`，不是 `resolvedMessage`
- 第 183 行的 `eventPublisher.publishEvent(new MessageSaveEvent(..., message))` 不变

### 数据流

```
用户输入: "刚那个作业再查一遍"
  ↓
CoreferenceResolutionService
  - 读取 ChatMemory 历史（conversationId）
  - 调用 ChatClient（指代消解专用）
  - System Prompt: "根据对话历史补全指代"
  - 返回: "查询作业ID为3的详情"
  ↓
主 ChatClient
  - 输入: "查询作业ID为3的详情"
  - 调用工具: getJobDetails(jobId=3)
  - 返回: 作业详情
  ↓
ChatMemory 存储:
  - 用户消息: "刚那个作业再查一遍" (原始)
  - 助手消息: "作业ID为3，名称为xxx..." (响应)
```

## 实施细节

### 1. 创建 CoreferenceResolutionService

**文件路径**：`src/main/java/com/dataplatform/ai/resolution/CoreferenceResolutionService.java`

**依赖注入**：
- `ChatModel`（与主流程共用，默认 DeepSeek-V3）
- `ChatMemory`（与主流程共用，Redis-based）

**方法实现**（API 已通过 Context7 查证 Spring AI 1.0.3）：
```java
@Service
@Slf4j
public class CoreferenceResolutionService {
    
    private final ChatClient resolutionChatClient;
    
    public CoreferenceResolutionService(ChatModel chatModel, ChatMemory chatMemory) {
        this.resolutionChatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }
    
    public String resolveAndRewrite(String userMessage, String conversationId) {
        try {
            String resolved = resolutionChatClient.prompt()
                .system(RESOLUTION_SYSTEM_PROMPT)
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
            
            // 降级策略：如果返回空或失败，返回原始消息
            return (resolved != null && !resolved.trim().isEmpty()) ? resolved.trim() : userMessage;
        } catch (Exception e) {
            log.error("指代消解失败，降级返回原始消息: {}", e.getMessage());
            return userMessage;  // 降级策略：返回原始消息
        }
    }
}
```

### 2. System Prompt 定义

**文件路径**：`src/main/java/com/dataplatform/ai/resolution/CoreferenceResolutionService.java`（常量）

**内容**：见上文"System Prompt"部分

### 3. AiAssistantController 改造

**改造位置**：`chatStream()` 方法，第 207 行附近

**改造内容**：
1. 注入 `CoreferenceResolutionService`
2. 在构建主 ChatClient 的 prompt 之前，调用 `resolveAndRewrite()`
3. 用改写后的消息调用主 ChatClient
4. 日志记录原始消息和改写结果

### 4. 配置与依赖

**无需修改**：
- Spring AI 版本（1.0.0）
- Redis 配置
- ChatMemory 配置（自动配置）

**需确认**：
- DeepSeek-V3 已配置为主模型（`@Primary`）
- ChatMemory 实例已注入到 ApplicationContext

## 测试验收

### 测试用例

#### 用例1：指代消解（作业ID）
```
用户: 查询作业ID为3的作业
AI: [返回作业3详情]
用户: 刚那个作业再查一遍
AI: [理解为"查询作业ID为3的详情"，返回作业3详情]
```

#### 用例2：指代消解（数据源ID）
```
用户: 查询数据源ID为5的详情
AI: [返回数据源5详情]
用户: 这个数据源测试连接
AI: [理解为"测试数据源ID为5的连接"，调用 testDataSourceConnection(5)]
```

#### 用例3：原样透传（无指代）
```
用户: 查询作业ID为7的详情
AI: [返回作业7详情]
# 改写结果应为："查询作业ID为7的详情"（原样）
```

#### 用例4：口语化归一
```
用户: 最近的作业有哪些
AI: [返回作业列表]
用户: 再看一遍
AI: [理解为"查询最近的作业列表"，再次调用 listRecentJobs]
```

### 验收方式

1. **功能验收**：运行上述4个用例，检查 AI 是否正确理解并执行
2. **日志验证**：检查日志中 `指代消解：原始='...', 改写='...'`，确认改写逻辑正确
3. **ChatMemory 验证**：查看 Redis 中存储的对话历史，确认存的是原始消息
4. **性能验证**：测量指代消解耗时，应 <500ms

## 实施者决策

以下决策已明确：

1. **降级策略**：如果指代消解失败（LLM 超时、返回空），返回原始消息，继续主流程
2. **性能优化**：第一版不做正则预判优化，直接调用 LLM
3. **日志级别**：指代消解的日志用 `INFO`（可在实施时调整为 `DEBUG`）
4. **空消息处理**：LLM 返回空字符串时，返回原始消息

## 不确定项

无。所有技术细节已明确。

## 风险与缓解

### 风险1：指代消解错误
**场景**：LLM 把"刚那个作业"理解成错误的作业ID  
**缓解**：
1. System Prompt 中强调"从最近的对话中提取"
2. 测试阶段多验证边界用例
3. 保留日志，方便排查

### 风险2：性能劣化
**场景**：每次对话都要调一次 LLM 做指代消解，响应变慢  
**缓解**：
1. 指代消解用的是 DeepSeek-V3，推理速度快（~1s）
2. 可以加缓存：如果用户消息不包含指代词（正则预判），跳过消解

### 风险3：ChatMemory 混乱
**场景**：指代消解和主流程都读写 ChatMemory，可能相互干扰  
**缓解**：
1. 指代消解只读 ChatMemory，不写入
2. 主流程负责存储用户原始消息和 AI 响应
3. 两者使用相同的 conversationId，保证历史一致

## 实施顺序

1. 创建 `CoreferenceResolutionService`，实现 `resolveAndRewrite()`
2. 编写测试用例（纯 Prompt 验证，不需要单元测试）
3. 改造 `AiAssistantController`，接入指代消解
4. 端到端测试 4 个验收用例
5. 性能测试，确认 <500ms
6. 提交代码，更新 dev-notes

## 参考资料

- Spring AI 官方文档（通过 Context7 MCP 查询）
- 现有代码：`AiAssistantController.java`
- ChatMemory 配置：`application.yml`
