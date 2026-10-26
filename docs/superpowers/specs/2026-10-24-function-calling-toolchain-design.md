# Function Calling 工具链与对话持久化设计
This agent may use the context7 MCP server to fetch official library documentation for langgraph4j, spring ai, milvus.

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
5. 用户点击"新建会话"，前端跳转到空白会话开始对话（URL 不变），会话数据持久化到数据库

## 目标、非目标、反目标

### 目标
- **FAQ 工具**：新增 `queryFaq` 工具，支持关键词 LIKE 查询（`question` 或 `answer` 字段），返回前 5 条匹配记录
- **对话持久化**：每次 `/chat/stream` 调用，自动落库到 `conversations` 和 `messages` 表
- **会话管理**：支持创建新会话、列出历史会话、切换会话、获取会话详情
- **会话 ID 策略**：后端生成唯一会话 ID，前端通过请求参数传递当前会话 ID（URL 保持不变）
- **二次确认机制**：`@RequireConfirmation` 注解 + 拦截器 + 一次性 Token，后端兜底防止 AI 伪造
- **超时重试**：查询工具（`@Tool` + `@RetryableQuery`）自动重试 2 次，阶梯退避（1s、2s）
- **参数校验增强**：Spring AI 自带的 JSON Schema 校验 + 自定义校验逻辑（如枚举值、必填项）

### 非目标（本章不做）
- **多轮自动循环**：不实现"AI 自主决定调用多个工具"的 Agent Loop，只做单轮（用户问 → AI 调工具 → 回答）
- **向量检索 / RAG**：FAQ 查询用简单的 SQL LIKE，ch03 再升级为向量检索
- **Langgraph4j 集成**：当前单轮工具调用不需要状态机，下章多轮 Agent 时再引入
- **会话分类和智能分组**：不做自动检测会话主题、建议创建新会话等 AI 增强功能
- **会话列表搜索和过滤**：暂不支持搜索历史会话、按时间/类型筛选

### 反目标（做了就算失败）
- **AI 能绕过二次确认**：confirmed 令牌必须由后端签发，AI 在工具参数里传 `confirmed=true` 不能生效
- **写操作自动重试**：删除、发布操作失败不能重试（防止重复执行）
- **对话落库阻塞请求**：落库异步执行，不能因为数据库慢拖慢 SSE 流式响应

## 约束条件

### 技术栈（已定死，走不通停下来问）
- Spring Boot 3.x + Spring AI 1.0.3+Spring ai alibaba
- MyBatis-Plus 3.5.5（已有 CRUD 基础设施）
- MySQL 8.0（已有 Flyway 迁移管理）
- 不用 Langgraph4j（单轮调用场景它过重）

### 现有架构必须保留
- `/api/ai/chat/stream` 接口签名改为：GET 方法 + `@RequestParam String message` + `@RequestParam(required=false) Long conversationId`
- 流式响应格式不变（SSE 事件：`text`、`tool_start`、`tool_end`、`table_start`、`table_row`、`done`、`error`）
- 现有的 JobTool 和 DataSourceTool 继续工作，不破坏已有功能
- 前端路由保持 `/assistant`，不根据会话 ID 变化

### 安全要求
- confirmed 令牌使用 JWT 签名（HS256），5 分钟有效期
- 令牌用过即焚（Redis 或内存 Map 存储已用 token ID）
- 工具调用日志必须包含：用户 ID、会话 ID、工具名、参数、结果、执行时长

## 核心设计决策

### 0. 会话管理：ID 生成与前后端交互

**决策**：后端生成雪花 ID，前端通过请求参数传递，URL 保持不变。

**为什么不用 URL 路由（如 `/assistant/conversation/123`）**：
- 用户要求 URL 不变（保持 `/assistant`）
- 简化前端路由逻辑，避免处理 URL 参数解析和路由守卫
- 会话切换通过组件状态管理即可，无需浏览器历史记录

**会话 ID 生成策略**：
- 使用雪花算法（Snowflake）生成唯一 Long 型 ID
- 格式：时间戳（41bit）+ 机器 ID（10bit）+ 序列号（12bit）
- 优点：分布式环境下保证唯一性，按时间排序，性能高

**前后端交互流程**：
```typescript
// 前端伪代码
interface Conversation {
  id: number;
  title: string;
  lastMessageTime: string;
}

// 1. 用户点击"新建会话"
async function createNewConversation() {
  const resp = await axios.post('/api/ai/conversations');
  // 后端返回：{ id: 1234567890, title: "新会话", createdAt: "..." }
  
  // 切换到新会话（更新组件状态，不改变 URL）
  setCurrentConversationId(resp.data.id);
  setMessages([]);  // 清空消息列表
}

// 2. 用户发送消息
function sendMessage(text: string) {
  const eventSource = new EventSource(
    `/api/ai/chat/stream?message=${text}&conversationId=${currentConversationId}`
  );
  // 监听 SSE 事件...
}

// 3. 页面刷新后恢复会话
onMounted(async () => {
  // 从 localStorage 读取上次的会话 ID
  const lastConvId = localStorage.getItem('lastConversationId');
  if (lastConvId) {
    // 加载历史消息
    const messages = await axios.get(`/api/ai/conversations/${lastConvId}/messages`);
    setMessages(messages.data);
    setCurrentConversationId(lastConvId);
  } else {
    // 自动创建默认会话
    await createNewConversation();
  }
});
```

**后端接口设计**：
```java
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {
    
    @Autowired
    private IdGenerator idGenerator;  // 雪花 ID 生成器
    
    /**
     * 创建新会话（空白会话）
     */
    @PostMapping("/conversations")
    public ConversationVO createConversation() {
        Long conversationId = idGenerator.nextId();
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setUserId("qld_001");  // 暂时硬编码
        conv.setTitle("新会话");
        conv.setStatus(ConversationStatus.ACTIVE);
        conversationService.save(conv);
        
        return ConversationVO.from(conv);
    }
    
    /**
     * 聊天接口（带会话 ID）
     */
    @GetMapping("/chat/stream")
    public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
            @RequestParam String message,
            @RequestParam(required = false) Long conversationId) {
        
        // 如果没传 conversationId，自动创建
        if (conversationId == null) {
            conversationId = conversationService.createDefaultConversation("qld_001");
        }
        
        // 校验会话是否存在且属于当前用户
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals("qld_001")) {
            return Flux.error(new IllegalArgumentException("会话不存在或无权限"));
        }
        
        // 保存用户消息
        messageService.saveUserMessage(conversationId, message);
        
        // 调用 ChatClient 流式响应...
        return chatClient.prompt()...;
    }
    
    /**
     * 列出用户所有会话
     */
    @GetMapping("/conversations")
    public List<ConversationVO> listConversations() {
        List<Conversation> conversations = conversationService.listByUserId("qld_001");
        return conversations.stream()
            .map(ConversationVO::from)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取会话的历史消息
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageVO> getMessages(@PathVariable Long conversationId) {
        // 校验权限
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals("qld_001")) {
            throw new IllegalArgumentException("会话不存在或无权限");
        }
        
        List<Message> messages = messageService.listByConversationId(conversationId);
        return messages.stream()
            .map(MessageVO::from)
            .collect(Collectors.toList());
    }
    
    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable Long conversationId) {
        // 校验权限
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals("qld_001")) {
            throw new IllegalArgumentException("会话不存在或无权限");
        }
        
        // 软删除（标记为已删除）
        conversationService.softDelete(conversationId);
    }
}
```

**ID 生成器实现（基于百度 UidGenerator）**：
```java
@Component
public class IdGenerator {
    private final Snowflake snowflake;
    
    public IdGenerator() {
        // workerId 和 datacenterId 可从配置文件读取
        this.snowflake = new Snowflake(1L, 1L);
    }
    
    public Long nextId() {
        return snowflake.nextId();
    }
}

// 使用 Hutool 的雪花算法工具类
// 依赖：cn.hutool:hutool-all:5.8.16
```

**前端状态管理（Pinia Store）**：
```typescript
// stores/conversation.ts
import { defineStore } from 'pinia';

export const useConversationStore = defineStore('conversation', {
  state: () => ({
    currentConversationId: null as number | null,
    conversations: [] as Conversation[],
    messages: [] as Message[],
  }),
  
  actions: {
    async createNewConversation() {
      const resp = await axios.post('/api/ai/conversations');
      this.conversations.unshift(resp.data);
      this.switchToConversation(resp.data.id);
    },
    
    async switchToConversation(conversationId: number) {
      this.currentConversationId = conversationId;
      localStorage.setItem('lastConversationId', String(conversationId));
      
      // 加载历史消息
      const resp = await axios.get(`/api/ai/conversations/${conversationId}/messages`);
      this.messages = resp.data;
    },
    
    async loadConversations() {
      const resp = await axios.get('/api/ai/conversations');
      this.conversations = resp.data;
    },
  },
});
```

**我的判断**：
- URL 不变简化了前端路由，但牺牲了"通过链接分享会话"的能力
- 用 localStorage 保存最后访问的会话 ID，页面刷新后自动恢复
- 雪花 ID 生成器性能高（百万级 QPS），适合高并发场景
- 前端通过 Pinia 状态管理统一处理会话切换逻辑

---

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

**conversations**（会话表）：
- `id`：主键，BIGINT，雪花算法生成
- `user_id`：用户标识（VARCHAR(64)，当前硬编码为 "qld_001"）
- `title`：会话标题（VARCHAR(128)，默认"新会话"，可由用户修改）
- `status`：会话状态（VARCHAR(20)，枚举值：ACTIVE=进行中、CLOSED=已结束、DELETED=已删除）
- `created_at`：创建时间（DATETIME）
- `updated_at`：最后更新时间（DATETIME）
- `deleted_at`：软删除时间（DATETIME，NULL 表示未删除）

**索引设计**：
- PRIMARY KEY (`id`)
- INDEX `idx_user_status` (`user_id`, `status`, `deleted_at`) - 用于查询用户的活跃会话列表
- INDEX `idx_created_at` (`created_at`) - 用于按时间排序

**messages**（消息流水表）：
- `id`：主键，BIGINT 自增
- `conversation_id`：外键 → conversations.id（BIGINT）
- `role`：消息角色（VARCHAR(20)，枚举值：user / assistant / tool）
- `content`：消息正文（TEXT，assistant 纯工具调用时可为空）
- `tool_calls`：JSON 格式的工具调用申请（JSON，格式对齐 OpenAI）
- `tool_call_id`：工具消息对应的申请单 ID（VARCHAR(64)）
- `created_at`：创建时间（DATETIME）

**索引设计**：
- PRIMARY KEY (`id`)
- INDEX `idx_conversation_created` (`conversation_id`, `created_at`) - 用于查询会话的历史消息（按时间正序）
- FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`id`) ON DELETE CASCADE

**faq**（知识库表）：
- `id`：主键，BIGINT 自增
- `question`：问题（VARCHAR(256)）
- `answer`：答案（TEXT）
- `category`：分类（VARCHAR(64)）
- `created_at`：创建时间（DATETIME）
- `updated_at`：更新时间（DATETIME）

**索引设计**：
- PRIMARY KEY (`id`)
- FULLTEXT INDEX `ft_question_answer` (`question`, `answer`) - MySQL 全文索引，用于 MATCH AGAINST 查询（性能优于 LIKE）
- INDEX `idx_category` (`category`) - 用于按分类筛选

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

### SQL DDL 语句

```sql
-- V1.2__create_conversation_tables.sql

-- 会话表
CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT PRIMARY KEY COMMENT '会话 ID，雪花算法生成',
    user_id VARCHAR(64) NOT NULL COMMENT '用户标识',
    title VARCHAR(128) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE/CLOSED/DELETED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    deleted_at DATETIME NULL COMMENT '软删除时间',
    
    INDEX idx_user_status (user_id, status, deleted_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话表';

-- 消息流水表
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversation_id BIGINT NOT NULL COMMENT '所属会话 ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/tool',
    content TEXT NULL COMMENT '消息正文',
    tool_calls JSON NULL COMMENT '工具调用申请（JSON 格式）',
    tool_call_id VARCHAR(64) NULL COMMENT '工具消息对应的申请单 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_messages_conversation 
        FOREIGN KEY (conversation_id) 
        REFERENCES conversations(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 消息流水表';

-- 知识库表
CREATE TABLE IF NOT EXISTS faq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'FAQ ID',
    question VARCHAR(256) NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '答案',
    category VARCHAR(64) NULL COMMENT '分类',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FULLTEXT INDEX ft_question_answer (question, answer) WITH PARSER ngram,
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 知识库';

-- 插入测试数据
INSERT INTO faq (question, answer, category) VALUES
('作业OOM内存溢出怎么办', '1. 增加作业 JVM 堆内存大小（-Xmx 参数）\n2. 检查是否有内存泄漏（使用 MAT 分析 dump 文件）\n3. 优化 SQL 查询，减少结果集大小\n4. 启用增量同步，避免全量拉取', '作业问题'),
('作业执行很慢怎么排查', '1. 检查源库和目标库网络延迟\n2. 查看数据源是否有慢查询（开启 slow query log）\n3. 检查同步任务并行度配置\n4. 观察 CPU 和磁盘 IO 指标', '作业问题'),
('作业一直不执行是什么原因', '1. 检查 Flink 集群资源是否充足（TaskManager 数量）\n2. 查看作业状态是否为"已启动"（status=RUNNING）\n3. 检查 Flink Web UI 是否有异常日志\n4. 确认调度时间配置是否正确', '作业问题'),
('如何修改数据源连接信息', '在"数据源管理"页面点击"编辑"按钮，修改 URL、用户名、密码等配置，保存后需重启相关作业。', '数据源管理'),
('支持哪些数据库类型', '当前支持 MySQL、PostgreSQL、Oracle、SQL Server、MongoDB、ClickHouse、Kafka 等主流数据源。', '数据源管理');
```

---

## 实现路径

### 阶段 1：数据库迁移与基础设施
- 创建 Flyway 迁移脚本 `V1.2__create_conversation_tables.sql`
- 创建 `conversations`、`messages`、`faq` 三张表（包含索引）
- 插入 5 条 FAQ 测试数据（OOM、慢、不执行等）
- 添加依赖：Hutool（雪花 ID 生成器）
- 创建 `IdGenerator` 组件（封装雪花算法）

### 阶段 2：会话管理后端实现
- 创建实体类：`Conversation.java`、`Message.java`、`Faq.java`
- 创建 Mapper：`ConversationMapper`、`MessageMapper`、`FaqMapper`
- 创建 Service：`ConversationService`、`MessageService`
- 实现接口：
  - `POST /api/ai/conversations` - 创建新会话
  - `GET /api/ai/conversations` - 列出会话列表
  - `GET /api/ai/conversations/{id}/messages` - 获取历史消息
  - `DELETE /api/ai/conversations/{id}` - 软删除会话
- 修改 `GET /api/ai/chat/stream` 接口，支持 `conversationId` 参数

### 阶段 3：FAQ 工具实现
- 创建 `FaqTool.java`（`@Component` + `@Tool`）
- 实现 `queryFaq(keyword, category)` 方法
- 添加 `@RetryableQuery` 注解
- 在 `AiAssistantController` 注册该工具

### 阶段 4：二次确认基础设施
- 创建 `@RequireConfirmation` 注解
- 创建 `ConfirmationContext`（ThreadLocal）
- 创建 `ConfirmationInterceptor`（AOP 拦截器）
- 创建 `ConfirmationService`（生成/验证 JWT token）
- 创建 `NeedConfirmationException`
- 修改 `AiAssistantController`，捕获异常并返回 `confirmation_required` 事件
- 修改 `JobTool.deleteJob` 和 `JobTool.publishJob`，添加注解

### 阶段 5：对话持久化
- 修改 `AiAssistantController.chatStream`：
  - 调用前创建/获取 conversation
  - 保存 user 消息
  - 在 Flux 钩子里记录 tool 调用和 assistant 回复
  - 流结束时更新会话的 `updated_at`

### 阶段 6：前端会话管理（Vibe Coding）
- 创建 Pinia Store：`useConversationStore`
- 修改聊天页组件：
  - 添加"新建会话"按钮，点击后调用 API 并切换会话
  - 添加会话列表侧边栏（可选，显示历史会话）
  - 修改消息发送逻辑，带上当前 `conversationId` 参数
  - 页面加载时从 localStorage 恢复上次会话 ID，加载历史消息
- 适配二次确认对话框（识别 `confirmation_required` 事件）

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
6. **会话管理**：
   - 点击"新建会话"，前端立即跳转到空白会话（URL 保持 `/assistant`）
   - 数据库创建新的 conversation 记录，状态为 ACTIVE
   - 发送消息时，消息关联到当前会话 ID
   - 页面刷新后，自动恢复到上次的会话（加载历史消息）
   - 切换到历史会话，能看到该会话的完整对话记录
   - 删除会话后，该会话不再出现在列表中（软删除，`deleted_at` 不为 NULL）

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

## 前端设计

### UI 布局（聊天页改造）

**当前布局**（假设）：
```
┌───────────────────────────────────────┐
│  导航栏                                │
├───────────────────────────────────────┤
│                                       │
│  消息列表区域                          │
│                                       │
│                                       │
├───────────────────────────────────────┤
│  输入框 + 发送按钮                     │
└───────────────────────────────────────┘
```

**改造后布局**：
```
┌─────────────┬─────────────────────────┐
│ 会话列表    │  导航栏 + 新建会话按钮   │
│ (侧边栏)    ├─────────────────────────┤
│             │                         │
│ ● 新会话    │  消息列表区域            │
│ ○ OOM问题   │                         │
│ ○ 作业配置  │                         │
│             │                         │
│             ├─────────────────────────┤
│             │  输入框 + 发送按钮       │
└─────────────┴─────────────────────────┘
```

**组件结构**：
- `ConversationSidebar.vue`：会话列表侧边栏
  - 顶部：新建会话按钮
  - 列表：历史会话（标题 + 最后消息时间）
  - 底部：展开/收起按钮
- `ChatArea.vue`：聊天区域（原有组件）
  - 改造点：从 Store 读取当前会话的消息
- `MessageInput.vue`：输入框（原有组件）
  - 改造点：发送消息时带上 `conversationId`

### Pinia Store 设计

```typescript
// stores/conversation.ts
import { defineStore } from 'pinia';
import axios from 'axios';

export interface Conversation {
  id: number;
  title: string;
  status: 'ACTIVE' | 'CLOSED' | 'DELETED';
  createdAt: string;
  updatedAt: string;
}

export interface Message {
  id: number;
  conversationId: number;
  role: 'user' | 'assistant' | 'tool';
  content: string;
  toolCalls?: any[];
  toolCallId?: string;
  createdAt: string;
}

export const useConversationStore = defineStore('conversation', {
  state: () => ({
    // 当前选中的会话 ID
    currentConversationId: null as number | null,
    
    // 会话列表
    conversations: [] as Conversation[],
    
    // 当前会话的消息列表
    messages: [] as Message[],
    
    // 侧边栏展开/收起状态
    sidebarCollapsed: false,
  }),
  
  getters: {
    // 当前会话对象
    currentConversation(state): Conversation | null {
      return state.conversations.find(c => c.id === state.currentConversationId) || null;
    },
    
    // 活跃的会话列表（按更新时间倒序）
    activeConversations(state): Conversation[] {
      return state.conversations
        .filter(c => c.status === 'ACTIVE')
        .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
    },
  },
  
  actions: {
    /**
     * 初始化：页面加载时调用
     */
    async initialize() {
      // 1. 加载会话列表
      await this.loadConversations();
      
      // 2. 恢复上次的会话
      const lastConvId = localStorage.getItem('lastConversationId');
      if (lastConvId) {
        const convId = parseInt(lastConvId);
        const exists = this.conversations.some(c => c.id === convId);
        
        if (exists) {
          await this.switchToConversation(convId);
          return;
        }
      }
      
      // 3. 如果没有上次的会话或会话不存在，自动创建新会话
      await this.createNewConversation();
    },
    
    /**
     * 加载会话列表
     */
    async loadConversations() {
      const resp = await axios.get('/api/ai/conversations');
      this.conversations = resp.data;
    },
    
    /**
     * 创建新会话
     */
    async createNewConversation() {
      const resp = await axios.post('/api/ai/conversations');
      const newConv = resp.data as Conversation;
      
      // 添加到列表顶部
      this.conversations.unshift(newConv);
      
      // 切换到新会话
      await this.switchToConversation(newConv.id);
    },
    
    /**
     * 切换到指定会话
     */
    async switchToConversation(conversationId: number) {
      // 1. 更新当前会话 ID
      this.currentConversationId = conversationId;
      
      // 2. 保存到 localStorage
      localStorage.setItem('lastConversationId', String(conversationId));
      
      // 3. 加载该会话的历史消息
      await this.loadMessages(conversationId);
    },
    
    /**
     * 加载会话的历史消息
     */
    async loadMessages(conversationId: number) {
      const resp = await axios.get(`/api/ai/conversations/${conversationId}/messages`);
      this.messages = resp.data;
    },
    
    /**
     * 添加用户消息（本地暂存，等待 SSE 流返回结果）
     */
    addUserMessage(content: string) {
      this.messages.push({
        id: Date.now(),  // 临时 ID
        conversationId: this.currentConversationId!,
        role: 'user',
        content,
        createdAt: new Date().toISOString(),
      });
    },
    
    /**
     * 添加助手消息（从 SSE 流累积）
     */
    addAssistantMessage(content: string) {
      this.messages.push({
        id: Date.now(),
        conversationId: this.currentConversationId!,
        role: 'assistant',
        content,
        createdAt: new Date().toISOString(),
      });
    },
    
    /**
     * 删除会话
     */
    async deleteConversation(conversationId: number) {
      await axios.delete(`/api/ai/conversations/${conversationId}`);
      
      // 从列表移除
      this.conversations = this.conversations.filter(c => c.id !== conversationId);
      
      // 如果删除的是当前会话，创建新会话
      if (this.currentConversationId === conversationId) {
        await this.createNewConversation();
      }
    },
    
    /**
     * 更新会话标题（用户手动修改）
     */
    async updateConversationTitle(conversationId: number, title: string) {
      await axios.patch(`/api/ai/conversations/${conversationId}`, { title });
      
      const conv = this.conversations.find(c => c.id === conversationId);
      if (conv) {
        conv.title = title;
      }
    },
  },
});
```

### 聊天页面改造

```vue
<!-- pages/assistant/index.vue -->
<template>
  <div class="assistant-page">
    <!-- 会话列表侧边栏 -->
    <ConversationSidebar 
      v-if="!conversationStore.sidebarCollapsed"
      @create-new="handleCreateNew"
      @switch="handleSwitch"
    />
    
    <!-- 聊天区域 -->
    <div class="chat-container">
      <!-- 导航栏 -->
      <div class="header">
        <button @click="conversationStore.sidebarCollapsed = !conversationStore.sidebarCollapsed">
          {{ conversationStore.sidebarCollapsed ? '展开' : '收起' }}
        </button>
        <button @click="handleCreateNew" class="btn-new-conversation">
          + 新建会话
        </button>
      </div>
      
      <!-- 消息列表 -->
      <div class="messages" ref="messagesContainer">
        <MessageItem 
          v-for="msg in conversationStore.messages" 
          :key="msg.id"
          :message="msg"
        />
      </div>
      
      <!-- 输入框 -->
      <MessageInput @send="handleSendMessage" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useConversationStore } from '@/stores/conversation';
import ConversationSidebar from '@/components/ConversationSidebar.vue';
import MessageItem from '@/components/MessageItem.vue';
import MessageInput from '@/components/MessageInput.vue';

const conversationStore = useConversationStore();

onMounted(async () => {
  // 页面加载时初始化
  await conversationStore.initialize();
});

async function handleCreateNew() {
  await conversationStore.createNewConversation();
}

async function handleSwitch(conversationId: number) {
  await conversationStore.switchToConversation(conversationId);
}

function handleSendMessage(text: string) {
  if (!conversationStore.currentConversationId) {
    console.error('当前没有活跃的会话');
    return;
  }
  
  // 1. 添加用户消息到界面
  conversationStore.addUserMessage(text);
  
  // 2. 建立 SSE 连接
  const conversationId = conversationStore.currentConversationId;
  const url = `/api/ai/chat/stream?message=${encodeURIComponent(text)}&conversationId=${conversationId}`;
  const eventSource = new EventSource(url);
  
  let assistantContent = '';
  
  eventSource.addEventListener('text', (e) => {
    const data = JSON.parse(e.data);
    assistantContent += data.text;
    // 实时更新界面（可以用 reactive ref）
  });
  
  eventSource.addEventListener('done', (e) => {
    // 流结束，添加完整的助手消息
    conversationStore.addAssistantMessage(assistantContent);
    eventSource.close();
  });
  
  eventSource.addEventListener('error', (e) => {
    console.error('SSE 连接错误', e);
    eventSource.close();
  });
  
  eventSource.addEventListener('confirmation_required', (e) => {
    // 处理二次确认（弹框）
    const data = JSON.parse(e.data);
    handleConfirmationRequired(data.token, data.message, text, conversationId);
  });
}

function handleConfirmationRequired(token: string, message: string, originalMessage: string, conversationId: number) {
  // 弹出确认对话框
  if (confirm(message)) {
    // 用户确认，带上 token 重新发送
    const url = `/api/ai/chat/stream?message=${encodeURIComponent(originalMessage)}&conversationId=${conversationId}`;
    const eventSource = new EventSource(url, {
      headers: {
        'X-Confirmation-Token': token,
      },
    });
    // ... 继续处理流
  }
}
</script>

<style scoped>
.assistant-page {
  display: flex;
  height: 100vh;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.btn-new-conversation {
  background: #1976d2;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
}
</style>
```

### 会话列表侧边栏组件

```vue
<!-- components/ConversationSidebar.vue -->
<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <button @click="$emit('create-new')" class="btn-new">
        + 新建会话
      </button>
    </div>
    
    <div class="conversation-list">
      <div 
        v-for="conv in conversationStore.activeConversations" 
        :key="conv.id"
        class="conversation-item"
        :class="{ active: conv.id === conversationStore.currentConversationId }"
        @click="$emit('switch', conv.id)"
      >
        <div class="conv-title">{{ conv.title }}</div>
        <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useConversationStore } from '@/stores/conversation';

const conversationStore = useConversationStore();

defineEmits<{
  'create-new': [];
  'switch': [conversationId: number];
}>();

function formatTime(timestamp: string): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  
  if (diffMins < 1) return '刚刚';
  if (diffMins < 60) return `${diffMins}分钟前`;
  if (diffMins < 1440) return `${Math.floor(diffMins / 60)}小时前`;
  return date.toLocaleDateString();
}
</script>

<style scoped>
.sidebar {
  width: 260px;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.btn-new {
  width: 100%;
  padding: 12px;
  background: #1976d2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
}

.conversation-item:hover {
  background: #f5f5f5;
}

.conversation-item.active {
  background: #e3f2fd;
  border-left: 3px solid #1976d2;
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  color: #999;
}
</style>
```

### 数据流图

```
用户操作                前端 Store              后端 API              数据库
   │                       │                       │                     │
   ├─ 点击"新建会话" ──────>│                       │                     │
   │                       ├─ POST /conversations ─>│                     │
   │                       │                       ├─ INSERT INTO ───────>│
   │                       │<─ {id, title, ...} ───┤                     │
   │                       ├─ 更新 Store 状态      │                     │
   │                       ├─ localStorage.set     │                     │
   │<─ 界面跳转到新会话 ───┤                       │                     │
   │                       │                       │                     │
   ├─ 发送消息 ───────────>│                       │                     │
   │                       ├─ 添加 user 消息       │                     │
   │                       ├─ EventSource(...?conversationId=123) ──────>│
   │                       │                       ├─ INSERT user msg ───>│
   │                       │                       ├─ 调用 ChatClient    │
   │                       │<─ SSE: text ──────────┤                     │
   │<─ 实时显示回复 ────────┤                       │                     │
   │                       │<─ SSE: done ──────────┤                     │
   │                       │                       ├─ INSERT assistant ──>│
   │                       ├─ 添加 assistant 消息  │                     │
   │                       │                       │                     │
   ├─ 页面刷新 ───────────>│                       │                     │
   │                       ├─ localStorage.get     │                     │
   │                       ├─ GET /conversations/123/messages ───────────>│
   │                       │                       │<─ SELECT * ─────────┤
   │                       │<─ [...历史消息...] ───┤                     │
   │<─ 恢复会话 ───────────┤                       │                     │
```

---

## 后端接口设计

### API 列表

| 方法 | 路径 | 说明 | 请求参数 | 响应示例 |
|------|------|------|----------|----------|
| POST | `/api/ai/conversations` | 创建新会话 | 无 | `{id, title, status, createdAt}` |
| GET | `/api/ai/conversations` | 列出会话列表 | 无 | `[{id, title, updatedAt}, ...]` |
| GET | `/api/ai/conversations/{id}/messages` | 获取会话消息 | 路径参数：id | `[{role, content, createdAt}, ...]` |
| PATCH | `/api/ai/conversations/{id}` | 更新会话标题 | 请求体：`{title}` | `{id, title, ...}` |
| DELETE | `/api/ai/conversations/{id}` | 删除会话 | 路径参数：id | 无响应体（204） |
| GET | `/api/ai/chat/stream` | 聊天流式响应 | `message`, `conversationId` | SSE 流 |

### 接口详细设计

#### 1. 创建新会话

```java
@PostMapping("/conversations")
public ResponseEntity<ConversationVO> createConversation(
        @RequestHeader(value = "X-User-Id", defaultValue = "qld_001") String userId) {
    
    Long conversationId = idGenerator.nextId();
    
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    conversation.setUserId(userId);
    conversation.setTitle("新会话");
    conversation.setStatus(ConversationStatus.ACTIVE);
    conversationService.save(conversation);
    
    return ResponseEntity.ok(ConversationVO.from(conversation));
}
```

**响应示例**：
```json
{
  "id": 1234567890123456,
  "title": "新会话",
  "status": "ACTIVE",
  "createdAt": "2024-10-24T10:30:00",
  "updatedAt": "2024-10-24T10:30:00"
}
```

#### 2. 列出会话列表

```java
@GetMapping("/conversations")
public ResponseEntity<List<ConversationVO>> listConversations(
        @RequestHeader(value = "X-User-Id", defaultValue = "qld_001") String userId) {
    
    List<Conversation> conversations = conversationService.lambdaQuery()
        .eq(Conversation::getUserId, userId)
        .eq(Conversation::getStatus, ConversationStatus.ACTIVE)
        .isNull(Conversation::getDeletedAt)
        .orderByDesc(Conversation::getUpdatedAt)
        .list();
    
    List<ConversationVO> vos = conversations.stream()
        .map(ConversationVO::from)
        .collect(Collectors.toList());
    
    return ResponseEntity.ok(vos);
}
```

**响应示例**：
```json
[
  {
    "id": 1234567890123456,
    "title": "OOM 内存溢出问题",
    "status": "ACTIVE",
    "createdAt": "2024-10-24T10:30:00",
    "updatedAt": "2024-10-24T11:45:00"
  },
  {
    "id": 1234567890123455,
    "title": "作业配置咨询",
    "status": "ACTIVE",
    "createdAt": "2024-10-23T15:20:00",
    "updatedAt": "2024-10-23T16:10:00"
  }
]
```

#### 3. 获取会话消息

```java
@GetMapping("/conversations/{conversationId}/messages")
public ResponseEntity<List<MessageVO>> getMessages(
        @PathVariable Long conversationId,
        @RequestHeader(value = "X-User-Id", defaultValue = "qld_001") String userId) {
    
    // 权限校验
    Conversation conversation = conversationService.getById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        throw new ForbiddenException("会话不存在或无权限访问");
    }
    
    List<Message> messages = messageService.lambdaQuery()
        .eq(Message::getConversationId, conversationId)
        .orderByAsc(Message::getCreatedAt)
        .list();
    
    List<MessageVO> vos = messages.stream()
        .map(MessageVO::from)
        .collect(Collectors.toList());
    
    return ResponseEntity.ok(vos);
}
```

**响应示例**：
```json
[
  {
    "id": 1,
    "role": "user",
    "content": "作业 1 的数据源是什么？",
    "toolCalls": null,
    "toolCallId": null,
    "createdAt": "2024-10-24T10:31:00"
  },
  {
    "id": 2,
    "role": "assistant",
    "content": null,
    "toolCalls": [
      {
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "getJobDetails",
          "arguments": "{\"jobId\": 1}"
        }
      }
    ],
    "toolCallId": null,
    "createdAt": "2024-10-24T10:31:05"
  },
  {
    "id": 3,
    "role": "tool",
    "content": "{\"sourceDatasourceId\": 10, \"jobName\": \"测试作业\"}",
    "toolCalls": null,
    "toolCallId": "call_abc123",
    "createdAt": "2024-10-24T10:31:06"
  },
  {
    "id": 4,
    "role": "assistant",
    "content": "作业 1 的数据源 ID 是 10，作业名称是"测试作业"。",
    "toolCalls": null,
    "toolCallId": null,
    "createdAt": "2024-10-24T10:31:08"
  }
]
```

#### 4. 更新会话标题

```java
@PatchMapping("/conversations/{conversationId}")
public ResponseEntity<ConversationVO> updateConversation(
        @PathVariable Long conversationId,
        @RequestBody UpdateConversationRequest request,
        @RequestHeader(value = "X-User-Id", defaultValue = "qld_001") String userId) {
    
    // 权限校验
    Conversation conversation = conversationService.getById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        throw new ForbiddenException("会话不存在或无权限修改");
    }
    
    // 更新标题
    if (request.getTitle() != null) {
        conversation.setTitle(request.getTitle());
    }
    conversationService.updateById(conversation);
    
    return ResponseEntity.ok(ConversationVO.from(conversation));
}

// 请求 DTO
@Data
public class UpdateConversationRequest {
    private String title;
}
```

#### 5. 删除会话（软删除）

```java
@DeleteMapping("/conversations/{conversationId}")
public ResponseEntity<Void> deleteConversation(
        @PathVariable Long conversationId,
        @RequestHeader(value = "X-User-Id", defaultValue = "qld_001") String userId) {
    
    // 权限校验
    Conversation conversation = conversationService.getById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        throw new ForbiddenException("会话不存在或无权限删除");
    }
    
    // 软删除：设置 deleted_at 时间戳
    conversation.setDeletedAt(LocalDateTime.now());
    conversation.setStatus(ConversationStatus.DELETED);
    conversationService.updateById(conversation);
    
    return ResponseEntity.noContent().build();
}
```

#### 6. 聊天流式响应（改造）

```java
@GetMapping("/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false) Long conversationId,
        @RequestHeader(value = "X-User-Id", defaultValue = "qld_001") String userId,
        @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
    
    // 1. 如果没传 conversationId，自动创建
    if (conversationId == null) {
        conversationId = createDefaultConversation(userId);
    }
    
    // 2. 校验会话权限
    Conversation conversation = conversationService.getById(conversationId);
    if (conversation == null || !conversation.getUserId().equals(userId)) {
        return Flux.error(new ForbiddenException("会话不存在或无权限访问"));
    }
    
    // 3. 处理二次确认 token
    if (confirmationToken != null) {
        confirmationService.validateAndConsume(confirmationToken);
        ConfirmationContext.setConfirmed(true);
    }
    
    // 4. 保存用户消息
    Message userMsg = new Message();
    userMsg.setConversationId(conversationId);
    userMsg.setRole(MessageRole.USER);
    userMsg.setContent(message);
    messageService.save(userMsg);
    
    // 5. 调用 ChatClient 流式响应
    final Long finalConversationId = conversationId;
    Flux<ServerSentEvent<Map<String, Object>>> responseFlux = chatClient.prompt()
        .user(message)
        .stream()
        .chatResponse()
        // ... 构建 SSE 事件流
        .doOnComplete(() -> {
            // 流结束后，更新会话的 updated_at
            conversationService.lambdaUpdate()
                .eq(Conversation::getId, finalConversationId)
                .set(Conversation::getUpdatedAt, LocalDateTime.now())
                .update();
        })
        .onErrorResume(NeedConfirmationException.class, ex -> {
            // 处理二次确认异常
            String token = confirmationService.generateToken(ex.getToolName(), ex.getToolParams());
            Map<String, Object> data = Map.of(
                "token", token,
                "message", ex.getMessage()
            );
            return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("confirmation_required")
                .data(data)
                .build());
        });
    
    return responseFlux;
}

private Long createDefaultConversation(String userId) {
    Long conversationId = idGenerator.nextId();
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    conversation.setUserId(userId);
    conversation.setTitle("新会话");
    conversation.setStatus(ConversationStatus.ACTIVE);
    conversationService.save(conversation);
    return conversationId;
}
```

### 实体类设计

```java
// Conversation.java
@Data
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private String userId;
    
    private String title;
    
    private ConversationStatus status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    private LocalDateTime deletedAt;
}

// ConversationStatus.java
public enum ConversationStatus {
    ACTIVE("进行中"),
    CLOSED("已结束"),
    DELETED("已删除");
    
    private final String description;
    
    ConversationStatus(String description) {
        this.description = description;
    }
}

// Message.java
@Data
@TableName("messages")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long conversationId;
    
    private MessageRole role;
    
    private String content;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ToolCall> toolCalls;
    
    private String toolCallId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

// MessageRole.java
public enum MessageRole {
    USER, ASSISTANT, TOOL
}

// ToolCall.java (对齐 OpenAI 格式)
@Data
public class ToolCall {
    private String id;
    private String type;  // "function"
    private FunctionCall function;
    
    @Data
    public static class FunctionCall {
        private String name;
        private String arguments;  // JSON 字符串
    }
}

// Faq.java
@Data
@TableName("faq")
public class Faq {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String question;
    
    private String answer;
    
    private String category;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### VO 设计

```java
// ConversationVO.java
@Data
public class ConversationVO {
    private Long id;
    private String title;
    private String status;
    private String createdAt;
    private String updatedAt;
    
    public static ConversationVO from(Conversation conversation) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setStatus(conversation.getStatus().name());
        vo.setCreatedAt(conversation.getCreatedAt().toString());
        vo.setUpdatedAt(conversation.getUpdatedAt().toString());
        return vo;
    }
}

// MessageVO.java
@Data
public class MessageVO {
    private Long id;
    private String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private String createdAt;
    
    public static MessageVO from(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole().name().toLowerCase());
        vo.setContent(message.getContent());
        vo.setToolCalls(message.getToolCalls());
        vo.setToolCallId(message.getToolCallId());
        vo.setCreatedAt(message.getCreatedAt().toString());
        return vo;
    }
}
```

---

## 已确定的设计决策

### 1. 用户 ID 来源：硬编码为 "qld_001"
- `/api/ai/chat/stream` 保持 `permitAll()`，不加认证拦截
- `conversations.user_id` 字段暂时写死为 `"qld_001"`
- **技术债务标注**：ch03 接入真正的 JWT 认证时再改（从 `SecurityContextHolder` 获取）

### 2. 会话生命周期：主动创建，不自动结束
- **创建时机**：用户点击"新建会话"按钮，或发送消息时如果没传 `conversationId` 则自动创建
- **状态流转**：
  - 新建时：`ACTIVE`（进行中）
  - 用户删除：`DELETED`（软删除，`deleted_at` 记录删除时间）
  - 不自动设置为"已结束"（保留扩展性，未来可支持用户手动关闭会话）
- **简化逻辑**：取消"一轮问答结束即结束"的设计，会话保持 ACTIVE 状态，允许多轮对话

### 3. 会话 ID 策略：后端生成雪花 ID
- 使用 Hutool 的 `Snowflake` 工具类生成唯一 Long 型 ID
- 优点：分布式环境下保证唯一性，按时间排序，无需数据库自增
- 配置：`workerId` 和 `datacenterId` 从配置文件读取（application.yml）

### 4. 已用 token 存储：数据库表 `used_confirmation_tokens`
- 表结构：
  - `id` BIGINT PRIMARY KEY AUTO_INCREMENT
  - `jti` VARCHAR(64) UNIQUE NOT NULL（token 唯一 ID）
  - `tool_name` VARCHAR(64) NOT NULL（绑定的工具名）
  - `tool_params` JSON NOT NULL（绑定的参数）
  - `used_at` DATETIME NOT NULL（使用时间）
  - `expires_at` DATETIME NOT NULL（过期时间，用于定时清理）
- 验证 token 时先查表：`SELECT 1 FROM used_confirmation_tokens WHERE jti = ?`，存在就拒绝
- 定时任务（`@Scheduled`）每小时清理过期记录：`DELETE FROM used_confirmation_tokens WHERE expires_at < NOW()`

### 5. 前端状态持久化：LocalStorage
- 存储最后访问的会话 ID：`localStorage.setItem('lastConversationId', conversationId)`
- 页面刷新时自动恢复：`localStorage.getItem('lastConversationId')`
- 如果本地存储的会话 ID 在数据库不存在（被删除或过期），自动创建新会话

---

## 下一步

所有设计决策已确定，现在调用 `superpowers:writing-plans` 生成实现计划。
