# Function Calling 工具链与对话持久化设计

## 意图与动机

给现有的 AI 助手装上**查询 FAQ 知识库**的能力，同时建立**对话持久化**基础设施，为后续章节的多轮 Agent、RAG 检索做准备。

**为什么现在做**：
- 现有的 JobTool 和 DataSourceTool 已经能查作业和数据源，但用户问"OOM 怎么办"这类故障诊断问题时无能为力
- 对话记录目前只存在内存（ChatMemory），会话重启后丢失，无法复盘和分析
- 需要建立**工具安全基础设施**（参数校验、重试、二次确认），为后续接入更多高危工具（删除数据、发布上线）打基础

**成功的标志**：
1. 用户问"Spark OOM 内存溢出怎么办"，AI 能从 FAQ 表查到答案并作答
2. 对话完整落库（用户消息、AI 回复、工具调用请求与结果），可追溯
3. 删除作业时，后端强制拦截要求二次确认，AI 无法绕过
4. 查询工具超时自动重试 2 次，写工具不重试

## 目标、非目标、反目标

### 目标
- **FAQ 工具**：新增 `queryFaq` 工具，支持关键词 LIKE 查询（`question` 或 `answer` 字段），返回前 5 条匹配记录
- **对话持久化**：每次 `/chat/stream` 调用，自动落库到 `conversations` 和 `messages` 表
- **二次确认机制**：`@RequireConfirmation` 注解 + 拦截器 + 一次性 Token，后端兜底防止 AI 伪造
- **超时重试**：查询工具（`@Tool` + `@RetryableQuery`）自动重试 2 次，阶梯退避（1s、2s）
- **参数校验增强**：Spring AI 自带的 JSON Schema 校验 + 自定义校验逻辑（如枚举值、必填项）

### 非目标（本章不做）
- **多轮自动循环**：不实现"AI 自主决定调用多个工具"的 Agent Loop，只做单轮（用户问 → AI 调工具 → 回答）
- **向量检索 / RAG**：FAQ 查询用简单的 SQL LIKE，ch03 再升级为向量检索
- **Langgraph4j 集成**：当前单轮工具调用不需要状态机，下章多轮 Agent 时再引入

### 反目标（做了就算失败）
- **AI 能绕过二次确认**：confirmed 令牌必须由后端签发，AI 在工具参数里传 `confirmed=true` 不能生效
- **写操作自动重试**：删除、发布操作失败不能重试（防止重复执行）
- **对话落库阻塞请求**：落库异步执行，不能因为数据库慢拖慢 SSE 流式响应

## 约束条件

### 技术栈（已定死，走不通停下来问）
- Spring Boot 3.x + Spring AI 1.0.3
- MyBatis-Plus 3.5.5（已有 CRUD 基础设施）
- MySQL 8.0（已有 Flyway 迁移管理）
- 不用 Langgraph4j（单轮调用场景它过重）

### 现有架构必须保留
- `/api/ai/chat/stream` 接口签名不变（GET 方法 + `@RequestParam String message`）
- 流式响应格式不变（SSE 事件：`text`、`tool_start`、`tool_end`、`table_start`、`table_row`、`done`、`error`）
- 现有的 JobTool 和 DataSourceTool 继续工作，不破坏已有功能

### 安全要求
- confirmed 令牌使用 JWT 签名（HS256），5 分钟有效期
- 令牌用过即焚（Redis 或内存 Map 存储已用 token ID）
- 工具调用日志必须包含：用户 ID、会话 ID、工具名、参数、结果、执行时长

## 核心设计决策

### 1. 对话持久化：拦截点选择

**决策**：在 Controller 返回 Flux 之前，用 `doOnNext` 和 `doOnComplete` 钩子异步落库。

**为什么不在 ChatClient 内部拦截**：
- Spring AI 的 `ChatClient` 没有暴露"消息发送前/后"的拦截钩子
- 在 `Advisor` 里拦截只能拿到 `UserMessage`，拿不到最终的 `AssistantMessage`（因为流式响应是懒加载的）

**实现方案**：
```java
// 伪代码示意
Flux<ServerSentEvent<Map<String, Object>>> response = chatClient.prompt()...stream()...;

// 在流开始前记录 user 消息
Long conversationId = conversationService.getOrCreateConversation(userId);
messageService.saveUserMessage(conversationId, userMessage);

// 在流结束后记录 assistant 消息
return response
    .doOnNext(event -> {
        if ("tool_start".equals(event.event())) {
            // 记录工具调用请求
        }
    })
    .doOnComplete(() -> {
        // 记录最终的 assistant 回复（需要累积流式 token）
    });
```

**我的判断**：这是权衡后的最优解，不完美但可行。完美的方案需要改 Spring AI 源码，不现实。

---

### 2. 二次确认：Token 生成与验证流程

**决策**：方案 2（请求上下文 + 注解）

**流程**：
1. 工具方法加 `@RequireConfirmation(operation = "删除作业")`
2. AOP 拦截器在方法执行前检查 `ConfirmationContext.isConfirmed()`
3. 第一次调用（未确认）→ 生成 JWT token，抛 `NeedConfirmationException`
4. Controller 捕获异常，返回 SSE 事件：`{event: "confirmation_required", token: "...", message: "..."}`
5. 前端弹框，用户点确认后，在 HTTP Header 加 `X-Confirmation-Token: xxx` 重新请求
6. 拦截器验证 token（签名、过期时间、是否已用、绑定的工具名+参数是否匹配）
7. 验证通过 → `ConfirmationContext.setConfirmed(true)`，工具方法正常执行

**Token 结构（JWT Payload）**：
```json
{
  "jti": "uuid",  // 唯一 ID，用于防重放
  "tool": "deleteJob",
  "params": {"jobId": 5},
  "exp": 1698765432  // 5 分钟后过期
}
```

**防伪造保证**：
- AI 看不到 `confirmed` 参数（不在工具方法签名里）
- Token 由后端签发，前端无法伪造（HMAC 密钥只在后端）
- Token 绑定具体操作（不能拿"删除作业 5"的 token 去删除作业 10）

---

### 3. 超时重试：Spring Retry 集成

**决策**：用 `@Retryable` 注解，只标记查询工具。

**实现**：
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
    maxAttempts = 3,  // 总共 3 次（首次 + 2 次重试）
    backoff = @Backoff(delay = 1000, multiplier = 2)  // 1s、2s
)
public @interface RetryableQuery {}

// 使用示例
@Tool
@RetryableQuery
public String queryFaq(@ToolParam String keyword) {
    // 查询 FAQ，超时或网络错误自动重试
}
```

**哪些工具标记为可重试**：
- ✅ `queryFaq`、`getJobDetails`、`listRecentJobs`、`searchDataSources` 等查询
- ❌ `deleteJob`、`publishJob`、`createJob` 等写操作

**我的判断**：查询幂等，重试安全；写操作可能重复执行（如删除两次），不能重试。

---

### 4. FAQ 工具设计

**工具签名**：
```java
@Tool(description = """
    查询常见问题知识库。根据关键词搜索故障诊断、性能优化等问题的答案。
    
    参数：
    - keyword: 搜索关键词，在问题和答案中模糊匹配（如"OOM"、"慢"、"报错"）
    - category: 可选，问题分类（"作业报错"、"作业执行变慢"、"作业未执行"）
    
    返回：匹配的 FAQ 列表（最多 5 条），包含问题、答案、分类
    """)
@RetryableQuery
public String queryFaq(
    @ToolParam(description = "搜索关键词") String keyword,
    @ToolParam(description = "问题分类，可选") String category
) {
    // SQL: SELECT * FROM faq 
    //      WHERE (question LIKE %keyword% OR answer LIKE %keyword%)
    //        AND (category = ? OR ? IS NULL)
    //      LIMIT 5
}
```

**测试数据（种子数据）**：
```sql
INSERT INTO faq (question, answer, category) VALUES
('Spark 任务 OOM 内存溢出怎么办？', 'map 端 OOM：增加 spark.executor.memory；reduce 端 OOM：增加 spark.sql.shuffle.partitions', '作业报错'),
('DB2 源库读取很慢', '检查：1. 是否有索引；2. 是否全表扫描；3. 网络带宽是否足够；4. 调整 fetchSize', '作业执行变慢'),
('作业一直不执行', '检查：1. 调度状态是否启用；2. Cron 表达式是否正确；3. 依赖的上游作业是否完成；4. 资源是否充足', '作业未执行'),
('Hive 表分区过多导致元数据查询慢', '方案：1. 合并小分区；2. 使用动态分区裁剪；3. 升级 Hive 版本', '作业执行变慢'),
('作业日志提示权限不足', '检查：1. 数据源用户权限；2. HDFS 目录权限；3. Kerberos 认证是否过期', '作业报错');
```

---

## 数据库设计

### 表结构（已给定，不变）

**conversations**（会话壳）：
- `id`：主键
- `user_id`：用户标识（当前从哪拿？猜测：JWT token 里的 userId，或固定用 "anonymous"）
- `status`：进行中/已转人工/已结束
- `created_at`、`updated_at`

**messages**（消息流水）：
- `id`：主键
- `conversation_id`：外键 → conversations.id
- `role`：user / assistant / tool
- `content`：消息正文（assistant 纯工具调用时可为空）
- `tool_calls`：JSON，assistant 消息带的工具调用申请（格式对齐 OpenAI）
- `tool_call_id`：tool 消息对应的申请单 ID
- `created_at`

**faq**（知识库）：
- `id`：主键
- `question`、`answer`、`category`
- `created_at`、`updated_at`

### 消息存储格式示例

**用户消息**：
```json
{
  "role": "user",
  "content": "作业 id 为 1 的数据源 url 是什么？",
  "tool_calls": null,
  "tool_call_id": null
}
```

**助手消息（带工具调用）**：
```json
{
  "role": "assistant",
  "content": null,  // 纯工具调用时为空
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "getJobDetails",
        "arguments": "{\"jobId\": 1}"
      }
    }
  ],
  "tool_call_id": null
}
```

**工具结果消息**：
```json
{
  "role": "tool",
  "content": "{\"sourceDatasourceId\": 10, \"jobName\": \"...\"}", 
  "tool_calls": null,
  "tool_call_id": "call_abc123"
}
```

**助手最终回复**：
```json
{
  "role": "assistant",
  "content": "作业 1 的数据源 ID 是 10，URL 是 jdbc:mysql://...",
  "tool_calls": null,
  "tool_call_id": null
}
```

---

## 实现路径

### 阶段 1：数据库迁移与种子数据
- 创建 Flyway 迁移脚本 `V1.2__create_conversation_tables.sql`
- 创建 `conversations`、`messages`、`faq` 三张表
- 插入 5 条 FAQ 测试数据（OOM、慢、不执行等）

### 阶段 2：FAQ 工具实现
- 创建 `FaqTool.java`（`@Component` + `@Tool`）
- 实现 `queryFaq(keyword, category)` 方法
- 添加 `@RetryableQuery` 注解
- 在 `AiAssistantController` 注册该工具

### 阶段 3：二次确认基础设施
- 创建 `@RequireConfirmation` 注解
- 创建 `ConfirmationContext`（ThreadLocal）
- 创建 `ConfirmationInterceptor`（AOP 拦截器）
- 创建 `ConfirmationService`（生成/验证 JWT token）
- 创建 `NeedConfirmationException`
- 修改 `AiAssistantController`，捕获异常并返回 `confirmation_required` 事件
- 修改 `JobTool.deleteJob` 和 `JobTool.publishJob`，添加注解

### 阶段 4：对话持久化
- 创建 `Conversation` 和 `Message` 实体类
- 创建 `ConversationService` 和 `MessageService`
- 修改 `AiAssistantController.chatStream`：
  - 调用前创建/获取 conversation
  - 保存 user 消息
  - 在 Flux 钩子里记录 tool 调用和 assistant 回复

### 阶段 5：前端适配（Vibe Coding）
- 修改聊天页，识别 `confirmation_required` 事件
- 弹出确认对话框，用户点击后带 `X-Confirmation-Token` 重新请求

---

## 验收标准

1. **FAQ 查询**：问"OOM 内存溢出怎么办"，返回 FAQ 答案
2. **参数校验**：调用工具缺少必填参数，Spring AI 自动提示"缺少 jobId 参数"
3. **查询重试**：数据库超时，自动重试 2 次（日志里能看到 3 次查询）
4. **二次确认**：
   - 问"删除作业 5"，前端弹框"此操作不可逆，确认删除？"
   - 不点确认，作业不会被删除
   - 点确认后，作业被删除
   - 修改 token 内容（如改 jobId），后端拒绝执行
5. **对话落库**：
   - 数据库 `conversations` 表有新记录
   - `messages` 表有 user、assistant、tool 三种角色的记录
   - tool 消息的 `tool_call_id` 能对上 assistant 消息的 `tool_calls[].id`

---

## 技术风险与缓解

### 风险 1：Spring AI 的工具调用信息难以拦截
**现象**：ChatClient 流式响应时，无法直接拿到"模型调用了哪些工具、参数是什么"。

**缓解**：
- 方案 A（已实现）：用 `ToolMonitorConfig.wrapTools()` 装饰器模式，在工具执行前后发射 `tool_start` 和 `tool_end` 事件
- 方案 B（备选）：解析 `ChatResponse.getMetadata()` 里的 `finishReason` 和原始响应，提取 tool_calls

**我的判断**：方案 A 已经在用，够用。

### 风险 2：流式响应中累积 assistant 内容困难
**现象**：SSE 流式返回的是逐 token 的碎片，需要累积成完整文本才能落库。

**缓解**：
- 在 Controller 里用 `StringBuilder` 累积
- `doOnNext` 时拼接，`doOnComplete` 时落库
- 如果流中断（用户关闭页面），`doOnCancel` 里也落库（标记为"未完成"）

### 风险 3：JWT 密钥泄露
**现象**：HMAC 密钥硬编码在代码里，提交到 Git。

**缓解**：
- 密钥存储在 `application.yml` 的 `confirmation.jwt.secret`
- 生产环境用环境变量覆盖（`CONFIRMATION_JWT_SECRET`）
- 密钥至少 32 字节随机生成

---

## 已确定的设计决策

### 1. 用户 ID 来源：硬编码为 "qld_001"
- `/api/ai/chat/stream` 保持 `permitAll()`，不加认证拦截
- `conversations.user_id` 字段暂时写死为 `"qld_001"`
- **技术债务标注**：ch03 接入真正的 JWT 认证时再改（从 `SecurityContextHolder` 获取）

### 2. 对话结束时机：一轮问答结束即结束
- 用户发送消息 → 创建新 conversation，状态为"进行中"
- AI 回答完成（`doOnComplete` 钩子触发）→ 更新状态为"已结束"
- 每次新消息都创建新的 conversation（不复用旧会话）
- 简化逻辑：不需要"转人工"状态（该功能留给 ch03 实现）

### 3. 已用 token 存储：数据库表 `used_confirmation_tokens`
- 表结构：
  - `id` BIGINT PRIMARY KEY AUTO_INCREMENT
  - `jti` VARCHAR(64) UNIQUE NOT NULL（token 唯一 ID）
  - `tool_name` VARCHAR(64) NOT NULL（绑定的工具名）
  - `tool_params` JSON NOT NULL（绑定的参数）
  - `used_at` DATETIME NOT NULL（使用时间）
  - `expires_at` DATETIME NOT NULL（过期时间，用于定时清理）
- 验证 token 时先查表：`SELECT 1 FROM used_confirmation_tokens WHERE jti = ?`，存在就拒绝
- 定时任务（`@Scheduled`）每小时清理过期记录：`DELETE FROM used_confirmation_tokens WHERE expires_at < NOW()`

---

## 下一步

所有设计决策已确定，现在调用 `superpowers:writing-plans` 生成实现计划。
