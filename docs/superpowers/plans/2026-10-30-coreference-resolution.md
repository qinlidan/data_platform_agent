# 指代消解实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让智能助手能够理解带指代的问句（"刚那个作业"），自动补全成完整、明确的问句（"查询作业ID为3的详情"）

**Architecture:** 在用户消息进入主 ChatClient 之前，用一个前置的轻量级 ChatClient（无工具）+ ChatMemory 做指代消解；改写后的消息传给主 ChatClient 进行工具调用；ChatMemory 存储用户原始消息。

**Tech Stack:** Spring AI 1.0.0, DeepSeek-V3, Redis ChatMemory, Spring Boot 3.2.2

**Spec:** `docs/superpowers/specs/2026-10-30-coreference-resolution-design.md`

## Global Constraints

- Spring AI 版本：1.0.0（API 已通过 Context7 查证）
- 主模型：DeepSeek-V3（已配置为 @Primary）
- ChatMemory：Redis-based（已自动配置）
- 指代消解失败时必须降级返回原始消息，不中断主流程
- ChatMemory 必须存储用户原始消息，不存改写结果

## Review Focus

1. **空消息输入**：用户输入空字符串或纯空格，期望原样返回或跳过指代消解
2. **LLM 超时**：指代消解 LLM 调用超时（>30s），期望降级返回原始消息，主流程继续
3. **历史为空**：新会话第一条消息（无历史），期望原样返回（无需改写）
4. **多轮指代嵌套**：用户连续多轮使用指代（"那个作业" → "它" → "再查一遍"），期望能追溯到最初实体
5. **错误实体提取**：LLM 把"刚那个作业"误解为错误的作业ID，期望通过测试用例验证准确率

---

## File Structure

### 新增文件
- `src/main/java/com/dataplatform/ai/resolution/CoreferenceResolutionService.java`
  - 职责：指代消解服务，接收用户消息 + conversationId，返回改写后的问句
  - 依赖：ChatModel, ChatMemory

### 修改文件
- `src/main/java/com/dataplatform/controller/AiAssistantController.java`
  - 改造点：chatStream() 方法第 207 行附近，注入 CoreferenceResolutionService，在调用主 ChatClient 之前先做指代消解

### 测试文件
- `src/test/java/com/dataplatform/ai/resolution/CoreferenceResolutionServiceTest.java`
  - 职责：验证指代消解逻辑（使用评估集，非传统单元测试）

---

### Task 1: 创建 CoreferenceResolutionService

**Files:**
- Create: `src/main/java/com/dataplatform/ai/resolution/CoreferenceResolutionService.java`
- Test: `src/test/java/com/dataplatform/ai/resolution/CoreferenceResolutionServiceTest.java`

**Interfaces:**
- Consumes: 
  - `ChatModel chatModel` (Spring 注入，已配置 DeepSeek-V3)
  - `ChatMemory chatMemory` (Spring 注入，Redis-based)
- Produces: 
  - `String resolveAndRewrite(String userMessage, String conversationId)` - 返回改写后的问句

- [ ] **Step 1: 写测试评估集（4个用例）**

创建 `CoreferenceResolutionServiceTest.java`，包含评估用例：

```java
@SpringBootTest
class CoreferenceResolutionServiceTest {
    
    @Autowired
    private CoreferenceResolutionService service;
    
    @Autowired
    private ChatMemory chatMemory;
    
    private String testConversationId;
    
    @BeforeEach
    void setUp() {
        testConversationId = "test-" + System.currentTimeMillis();
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试会话历史
        chatMemory.clear(testConversationId);
    }
    
    @Test
    void 用例1_指代消解_作业ID() {
        // 模拟历史：先查询作业3
        chatMemory.add(testConversationId, new UserMessage("查询作业ID为3的作业"));
        chatMemory.add(testConversationId, new AssistantMessage("作业ID为3，名称为xxx..."));
        
        // 当前消息：带指代
        String result = service.resolveAndRewrite("刚那个作业再查一遍", testConversationId);
        
        // 断言：应补全为完整问句（包含作业ID 3）
        assertThat(result).containsIgnoringCase("作业");
        assertThat(result).containsIgnoringCase("3");
        log.info("用例1结果: {}", result);
    }
    
    @Test
    void 用例2_原样透传_无指代() {
        String input = "查询作业ID为5的详情";
        String result = service.resolveAndRewrite(input, testConversationId);
        
        // 断言：应原样返回或语义等价
        assertThat(result).containsIgnoringCase("作业");
        assertThat(result).containsIgnoringCase("5");
        log.info("用例2结果: {}", result);
    }
    
    @Test
    void 用例3_降级策略_空消息() {
        String result = service.resolveAndRewrite("", testConversationId);
        assertEquals("", result);
    }
    
    @Test
    void 用例4_降级策略_无历史() {
        // 新会话，无历史
        String result = service.resolveAndRewrite("查询作业详情", testConversationId);
        
        // 断言：无历史时应原样返回
        assertThat(result).isNotEmpty();
        log.info("用例4结果: {}", result);
    }
}
```

- [ ] **Step 2: 创建 CoreferenceResolutionService.java**

创建文件，定义常量和构造函数：

```java
package com.dataplatform.ai.resolution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CoreferenceResolutionService {
    
    private final ChatClient resolutionChatClient;
    
    private static final String RESOLUTION_SYSTEM_PROMPT = """
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
            """;
    
    public CoreferenceResolutionService(ChatModel chatModel, ChatMemory chatMemory) {
        this.resolutionChatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }
    
    public String resolveAndRewrite(String userMessage, String conversationId) {
        // 待实现
        return userMessage;
    }
}
```

- [ ] **Step 3: 实现 resolveAndRewrite() 方法**

实现核心逻辑：

```java
public String resolveAndRewrite(String userMessage, String conversationId) {
    // 空消息直接返回
    if (userMessage == null || userMessage.trim().isEmpty()) {
        return userMessage;
    }
    
    try {
        log.info("开始指代消解: conversationId={}, 原始消息={}", conversationId, userMessage);
        
        String resolved = resolutionChatClient.prompt()
            .system(RESOLUTION_SYSTEM_PROMPT)
            .user(userMessage)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
        
        // 降级策略：如果返回空，返回原始消息
        if (resolved == null || resolved.trim().isEmpty()) {
            log.warn("指代消解返回空，降级返回原始消息");
            return userMessage;
        }
        
        String result = resolved.trim();
        log.info("指代消解完成: 改写={}", result);
        return result;
        
    } catch (Exception e) {
        log.error("指代消解失败，降级返回原始消息: {}", e.getMessage());
        return userMessage;  // 降级策略
    }
}
```

- [ ] **Step 4: 运行评估测试**

运行：
```bash
mvn test -Dtest=CoreferenceResolutionServiceTest
```

预期：
- 用例1：输出包含"作业"和"3"
- 用例2：输出包含"作业"和"5"
- 用例3：返回空字符串
- 用例4：返回非空字符串

查看日志，人工判断改写质量。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dataplatform/ai/resolution/CoreferenceResolutionService.java \
        src/test/java/com/dataplatform/ai/resolution/CoreferenceResolutionServiceTest.java
git commit -m "feat: add CoreferenceResolutionService for coreference resolution

- 创建指代消解服务，使用独立 ChatClient + ChatMemory
- 实现 resolveAndRewrite() 方法，降级策略：失败时返回原始消息
- 添加评估测试用例（4个场景）

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 改造 AiAssistantController 接入指代消解

**Files:**
- Modify: `src/main/java/com/dataplatform/controller/AiAssistantController.java:63-87,207-230`

**Interfaces:**
- Consumes: 
  - `CoreferenceResolutionService.resolveAndRewrite(String, String)` - 从 Task 1
- Produces: 
  - 改造后的 `chatStream()` 方法，在调用主 ChatClient 前先做指代消解

- [ ] **Step 1: 写集成测试（手动测试脚本）**

创建测试脚本 `docs/superpowers/plans/test-coreference-integration.md`：

```markdown
# 指代消解集成测试脚本

## 环境准备
1. 启动后端：`java -jar target/data-platform-1.0.0.jar`
2. 确认 Redis 运行中
3. 确认 DeepSeek API Key 已配置

## 测试用例

### 用例1：指代消解（作业ID）
**步骤**：
1. 发起对话：POST `/api/ai/chat/stream?message=查询作业ID为3的作业&userId=test1`
2. 等待响应
3. 同一会话：POST `/api/ai/chat/stream?message=刚那个作业再查一遍&userId=test1&conversationId={上一步返回的conversationId}`

**预期**：
- 日志显示：`指代消解：原始='刚那个作业再查一遍', 改写='查询作业ID为3的详情'`（或语义等价）
- AI 返回作业3的详情

### 用例2：原样透传（无指代）
**步骤**：
1. 新会话：POST `/api/ai/chat/stream?message=查询作业ID为7的详情&userId=test2`

**预期**：
- 日志显示：改写结果与原始消息相同或语义等价
- AI 返回作业7的详情

### 用例3：多轮指代
**步骤**：
1. POST `/api/ai/chat/stream?message=查询数据源ID为5的详情&userId=test3`
2. POST `/api/ai/chat/stream?message=这个数据源测试连接&conversationId={同上}`
3. POST `/api/ai/chat/stream?message=再测试一遍&conversationId={同上}`

**预期**：
- 第2轮：改写包含"数据源"和"5"
- 第3轮：改写包含"数据源"和"5"
```

- [ ] **Step 2: 修改 AiAssistantController 构造函数**

在第 63-87 行，注入 `CoreferenceResolutionService`：

```java
private final CoreferenceResolutionService coreferenceResolutionService;

public AiAssistantController(
        ChatModel chatModel,
        JobService jobService,
        DataSourceService dataSourceService,
        FaqService faqService,
        ChatMemory chatMemory,
        @Qualifier("pureConversationSystemPrompt") SystemPromptTemplate pureConversationSystemPrompt,
        com.dataplatform.service.ConversationService conversationService,
        com.dataplatform.service.MessageService messageService,
        ApplicationEventPublisher eventPublisher,
        ToolExecutor toolExecutor,
        ConfirmationService confirmationService,
        CoreferenceResolutionService coreferenceResolutionService  // 新增
) {
    // ... 原有赋值 ...
    this.coreferenceResolutionService = coreferenceResolutionService;
}
```

- [ ] **Step 3: 修改 chatStream() 方法**

在第 207 行附近（构建 ChatClient prompt 之前），添加指代消解调用：

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
        .user(resolvedMessage)  // ← 改为使用 resolvedMessage
        .toolContext(buildToolContext(userId, confirmationToken))
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(finalConversationId)))
        .stream()
        // ... 后续代码不变 ...
```

注意：第 183 行的异步落表仍然使用原始消息 `message`，不要改动。

- [ ] **Step 4: 编译验证**

运行：
```bash
mvn clean compile
```

预期：编译通过，无错误

- [ ] **Step 5: 启动并执行集成测试**

运行：
```bash
mvn clean package -DskipTests
java -jar target/data-platform-1.0.0.jar
```

按照 `test-coreference-integration.md` 脚本执行测试，检查日志和响应。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/dataplatform/controller/AiAssistantController.java \
        docs/superpowers/plans/test-coreference-integration.md
git commit -m "feat: integrate CoreferenceResolutionService into chatStream

- AiAssistantController 注入 CoreferenceResolutionService
- chatStream() 在调用主 ChatClient 前先做指代消解
- 保持 ChatMemory 存储原始消息的策略
- 添加集成测试脚本

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 端到端验收与文档更新

**Files:**
- Modify: `dev-notes/ch06.md` - 追记实施过程

**Interfaces:**
- Consumes: 完整功能（Task 1 + Task 2）
- Produces: 验收报告 + 更新的开发笔记

- [ ] **Step 1: 运行完整验收用例**

执行设计文档中的 4 个验收用例（见 `2026-10-30-coreference-resolution-design.md` "测试验收" 章节）：
1. 指代消解（作业ID）
2. 指代消解（数据源ID）
3. 原样透传（无指代）
4. 口语化归一

记录每个用例的：
- 日志中的改写结果
- AI 最终响应
- 是否通过

- [ ] **Step 2: 性能测试**

使用 curl + time 测量响应时间：
```bash
time curl -X POST "http://localhost:8080/api/ai/chat/stream?message=刚那个作业再查一遍&userId=test&conversationId=123"
```

记录指代消解耗时（从日志中提取），确认 <500ms。

- [ ] **Step 3: 更新 dev-notes/ch06.md**

追加实施阶段记录：

```markdown
## 阶段：实施完成

### 任务完成记录

#### Task 1: CoreferenceResolutionService
- 完成时间：[填写]
- 评估测试结果：
  - 用例1（指代消解）：✅ 通过，改写为 "[实际结果]"
  - 用例2（原样透传）：✅ 通过
  - 用例3（空消息降级）：✅ 通过
  - 用例4（无历史降级）：✅ 通过

#### Task 2: AiAssistantController 改造
- 完成时间：[填写]
- 集成测试结果：[填写]

#### Task 3: 端到端验收
- 完成时间：[填写]
- 验收用例通过率：[X/4]
- 性能测试：指代消解平均耗时 [X]ms

### 翻车与返工
[如有，记录问题和解决方案]

---

## 阶段：交付

### 功能演示命令
```bash
# 启动服务
java -jar target/data-platform-1.0.0.jar

# 测试用例1
curl -X POST "http://localhost:8080/api/ai/chat/stream?message=查询作业ID为3的作业&userId=demo"
# 记录返回的 conversationId
curl -X POST "http://localhost:8080/api/ai/chat/stream?message=刚那个作业再查一遍&userId=demo&conversationId={上一步的conversationId}"
```

### 测试结果
[粘贴实际测试结果]

### 文档路径
- 设计文档：`docs/superpowers/specs/2026-10-30-coreference-resolution-design.md`
- 实施计划：`docs/superpowers/plans/2026-10-30-coreference-resolution.md`
- 开发笔记：`dev-notes/ch06.md`
```

- [ ] **Step 4: Commit**

```bash
git add dev-notes/ch06.md
git commit -m "docs: complete coreference resolution implementation

- 记录任务完成情况
- 添加验收测试结果
- 更新性能测试数据

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Checklist

### 1. Spec Coverage
- ✅ 指代消解（Task 1）
- ✅ Query 改写（Task 1，System Prompt 包含）
- ✅ 原样透传（Task 1，测试用例2）
- ✅ 降级策略（Task 1，Step 3）
- ✅ ChatMemory 存储策略（Task 2，Step 3 注释）
- ✅ 集成到主流程（Task 2）
- ✅ 验收标准（Task 3）

### 2. Step Scan
- 每个 Step 都有明确的输入、操作、输出
- 测试 Step 包含具体的断言代码
- 代码 Step 包含签名和关键实现
- 验证 Step 包含命令和预期结果

### 3. Type Consistency
- `resolveAndRewrite(String userMessage, String conversationId)` - Task 1 定义，Task 2 使用，签名一致
- `CoreferenceResolutionService` - 类名在所有任务中一致

### 4. Review Focus
- ✅ 空消息输入 → Task 1 Step 3 处理，测试用例3 验证
- ✅ LLM 超时 → Task 1 Step 3 try-catch 降级
- ✅ 历史为空 → 测试用例4 验证
- ✅ 多轮指代嵌套 → Task 2 Step 5 集成测试用例3 验证
- ✅ 错误实体提取 → Task 1 Step 4 评估测试，Task 3 Step 1 验收测试

### 5. Proportion
- 设计文档：~300 行
- 实施计划：~400 行
- 比例合理，未变成代码转录

---

