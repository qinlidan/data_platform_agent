# AI 对话功能实施完成总结

## 🎯 实施概述

基于 Spring AI 1.0.3 实现的完整 AI 对话功能，包含二次确认机制、对话持久化、FAQ 知识库和 Token 防重放机制。

**完成时间**: 2026-10-24

---

## ✅ 已完成功能

### 1. 数据库基础设施

#### Flyway 迁移脚本
- **V1.2__create_conversation_tables.sql**: 创建会话和消息表
  - `conversations`: 存储用户会话（用户ID、标题、最后消息时间）
  - `messages`: 存储对话消息（会话ID、角色、内容、工具调用元数据）
  - `used_confirmation_tokens`: Token 防重放记录表
  - `faq`: FAQ 知识库表及种子数据（5条故障排查和性能优化问答）

#### 表结构验证
```sql
-- 已创建的表
conversations              -- 会话表
messages                   -- 消息表
used_confirmation_tokens   -- 已用 Token 表
faq                        -- FAQ 知识库
```

---

### 2. 二次确认基础设施（Task 3）

#### 核心组件

**@RequireConfirmation 注解**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireConfirmation {
    String operation();
    String riskLevel() default "medium";
}
```

**ConfirmationContext** - ThreadLocal 上下文
```java
public class ConfirmationContext {
    private static final ThreadLocal<String> confirmationTokenHolder = new ThreadLocal<>();
    public static void setToken(String token);
    public static String getToken();
    public static void clear();
    public static boolean hasToken();
}
```

**NeedConfirmationException** - 确认异常
```java
@Getter
public class NeedConfirmationException extends RuntimeException {
    private final String operation;
    private final String riskLevel;
    private final String confirmationToken;
}
```

**ConfirmationService** - Token 生成与验证
- JWT Token 生成（HS256 签名，5 分钟有效期）
- JTI（JWT ID）防重放机制
- Token 验证后自动记录到数据库

**ConfirmationInterceptor** - AOP 拦截器
```java
@Aspect
@Component
@Around("@annotation(com.dataplatform.ai.confirmation.RequireConfirmation)")
public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable
```

#### 工作流程

1. **首次调用**：工具方法抛出 `NeedConfirmationException`
2. **前端确认**：用户确认操作，携带 Token 重试
3. **Token 验证**：
   - 验证 JTI 未被使用（防重放）
   - 验证签名和工具名/方法名匹配
   - 验证通过后记录 JTI 到数据库
4. **执行操作**：放行方法执行

#### 控制器集成

**AiAssistantController.chatStream()**
```java
} catch (NeedConfirmationException e) {
    return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
            .event("confirmation_required")
            .data(Map.of(
                    "operation", e.getOperation(),
                    "riskLevel", e.getRiskLevel(),
                    "confirmationToken", e.getConfirmationToken(),
                    "message", e.getMessage()
            ))
            .build());
}
```

---

### 3. 对话持久化（Task 4）

#### 实体类

**Conversation** - 会话实体
```java
@Data
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String title;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
```

**Message** - 消息实体
```java
@Data
@TableName("messages")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;  // user, assistant, system
    private String content;
    private String toolCalls;  // JSON 格式的工具调用记录
    private LocalDateTime createdAt;
}
```

**UsedConfirmationToken** - Token 防重放记录
```java
@Data
@TableName("used_confirmation_tokens")
public class UsedConfirmationToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jti;  // JWT ID
    private String toolName;
    private String toolParams;  // JSON 格式
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;
}
```

#### 服务类

**ConversationService**
```java
public Long getOrCreateConversation(String userId) {
    // 查询或创建用户会话
}

public void updateLastMessage(Long conversationId) {
    // 更新最后消息时间
}
```

**MessageService**
```java
public void saveUserMessage(Long conversationId, String content) {
    // 保存用户消息
}

public void saveAssistantMessage(Long conversationId, String content, String toolCalls) {
    // 保存助手响应
}
```

#### 控制器集成

**chatStream 方法增强**
```java
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId
) {
    // 1. 创建或获取会话
    Long conversationId = conversationService.getOrCreateConversation(userId);
    
    // 2. 保存用户消息
    messageService.saveUserMessage(conversationId, message);
    
    // 3. 流式生成响应，累积内容
    StringBuilder assistantResponse = new StringBuilder();
    
    // 4. 流结束后保存助手响应
    .doOnComplete(() -> {
        if (assistantResponse.length() > 0) {
            messageService.saveAssistantMessage(conversationId, assistantResponse.toString(), null);
        }
    });
}
```

---

### 4. FAQ 知识库（Task 2）

#### 实体和 Mapper

**Faq 实体**
```java
@Data
@TableName("faq")
public class Faq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;  // troubleshooting, performance, usage
    private String question;
    private String answer;
    private String keywords;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**FaqMapper**
```java
@Mapper
public interface FaqMapper extends BaseMapper<Faq> {
}
```

#### FAQ 工具

**FaqTool.queryFaq()**
```java
@Tool(description = "查询常见问题知识库，支持按关键词和分类搜索")
public String queryFaq(
    @ToolParam(description = "搜索关键词") String keyword,
    @ToolParam(description = "分类：troubleshooting | performance | usage") String category
)
```

#### 种子数据

已插入 5 条 FAQ 数据：
1. Hive 作业执行失败排查（troubleshooting）
2. DB2 连接超时排查（troubleshooting）
3. Spark 任务性能优化（performance）
4. 大表迁移优化（performance）
5. 作业版本回滚（usage）

---

### 5. Token 防重放机制

#### 实现原理

1. **Token 生成时**：添加唯一 JTI（UUID）
   ```java
   String jti = java.util.UUID.randomUUID().toString();
   claims.put("jti", jti);
   ```

2. **Token 验证前**：检查 JTI 是否已使用
   ```java
   LambdaQueryWrapper<UsedConfirmationToken> wrapper = new LambdaQueryWrapper<>();
   wrapper.eq(UsedConfirmationToken::getJti, jti);
   if (usedTokenMapper.selectCount(wrapper) > 0) {
       return false; // Token 已使用
   }
   ```

3. **验证通过后**：记录 JTI 到数据库
   ```java
   UsedConfirmationToken usedToken = new UsedConfirmationToken();
   usedToken.setJti(jti);
   usedToken.setToolName(toolName);
   usedToken.setToolParams(params.toString());
   usedToken.setUsedAt(LocalDateTime.now());
   usedToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
   usedTokenMapper.insert(usedToken);
   ```

#### 防重放保证

- ✅ 同一 Token 只能使用一次
- ✅ Token 5 分钟后自动过期
- ✅ 数据库记录可审计

---

## 📂 文件清单

### 新建文件

#### 后端实体类
- `src/main/java/com/dataplatform/entity/Conversation.java`
- `src/main/java/com/dataplatform/entity/Message.java`
- `src/main/java/com/dataplatform/entity/UsedConfirmationToken.java`

#### Mapper
- `src/main/java/com/dataplatform/mapper/ConversationMapper.java`
- `src/main/java/com/dataplatform/mapper/MessageMapper.java`
- `src/main/java/com/dataplatform/mapper/UsedConfirmationTokenMapper.java`
- `src/main/java/com/dataplatform/mapper/FaqMapper.java`

#### 服务类
- `src/main/java/com/dataplatform/service/ConversationService.java`
- `src/main/java/com/dataplatform/service/MessageService.java`

#### 二次确认组件
- `src/main/java/com/dataplatform/ai/confirmation/RequireConfirmation.java`
- `src/main/java/com/dataplatform/ai/confirmation/ConfirmationContext.java`
- `src/main/java/com/dataplatform/ai/confirmation/NeedConfirmationException.java`
- `src/main/java/com/dataplatform/ai/confirmation/ConfirmationService.java`
- `src/main/java/com/dataplatform/ai/confirmation/ConfirmationInterceptor.java`

#### 数据库迁移
- `src/main/resources/db/migration/V1.2__create_conversation_tables.sql`

### 修改文件

#### 后端控制器
- `src/main/java/com/dataplatform/controller/AiAssistantController.java`
  - 注入 ConversationService 和 MessageService
  - chatStream 方法集成对话持久化
  - 添加 NeedConfirmationException 异常处理

#### 配置文件
- `pom.xml`
  - 添加 spring-boot-starter-aop 依赖（AOP 支持）

---

## 🔒 安全机制

### 1. 二次确认流程

**高风险操作保护**（如删除作业、发布作业）

1. 用户发起操作："删除作业 ID=123"
2. AI 调用 `deleteJob(123, false)`
3. `ConfirmationInterceptor` 拦截，抛出 `NeedConfirmationException`
4. 前端收到 `confirmation_required` 事件，显示确认弹窗
5. 用户点击"确认"，携带 Token 重试
6. `ConfirmationInterceptor` 验证 Token，放行执行
7. Token 标记为已使用，防止重放

### 2. Token 防重放

**防止攻击者重复使用截获的 Token**

- JTI（JWT ID）唯一标识每个 Token
- 数据库记录已使用的 JTI
- 验证前检查 JTI 是否已使用
- Token 5 分钟后过期（数据库可定期清理）

### 3. 数据隔离

**多用户隔离**

- `conversations` 表按 `user_id` 隔离
- `userId` 参数默认值为 `"anonymous"`
- 未来可集成 JWT 认证，从 Token 提取真实用户ID

---

## 🚀 测试验证

### 编译验证

```bash
cd D:/alert4
mvn clean package -DskipTests
```

**结果**: ✅ BUILD SUCCESS

### 数据库验证

```sql
-- 检查表是否创建
SHOW TABLES LIKE '%conversation%';
SHOW TABLES LIKE '%message%';
SHOW TABLES LIKE '%confirmation%';
SHOW TABLES LIKE '%faq%';

-- 检查 FAQ 数据
SELECT id, category, question FROM faq;
```

**结果**: ✅ 所有表已创建，5 条 FAQ 数据已插入

---

## 📊 架构图

### 二次确认流程

```
┌─────────┐      ┌─────────────┐      ┌──────────────────┐
│  前端   │─────▶│ Controller  │─────▶│ ConfirmationInter│
│ (Vue3)  │      │             │      │     ceptor       │
└─────────┘      └─────────────┘      └──────────────────┘
     │                  │                      │
     │                  │                      ▼
     │                  │              ┌───────────────┐
     │                  │              │ConfirmationSvc│
     │                  │              │ - generateToken│
     │                  │              │ - validateToken│
     │                  │              └───────────────┘
     │                  │                      │
     │                  ▼                      ▼
     │         ┌──────────────┐      ┌─────────────────┐
     │         │ SSE 事件流   │      │ used_confirmation│
     │         │confirmation_ │      │     _tokens     │
     │◀────────│  required    │      │   (防重放表)     │
     │         └──────────────┘      └─────────────────┘
     │
     │  (用户确认)
     │
     │         ┌─────────────┐
     └────────▶│ 重试请求    │
               │ (携带Token) │
               └─────────────┘
```

### 对话持久化流程

```
┌─────────┐      ┌─────────────┐      ┌──────────────────┐
│  前端   │─────▶│ Controller  │─────▶│ConversationService│
│ (发消息)│      │ chatStream  │      │                  │
└─────────┘      └─────────────┘      └──────────────────┘
                        │                      │
                        │                      ▼
                        │              ┌───────────────┐
                        │              │conversations  │
                        │              │ (创建或获取)   │
                        │              └───────────────┘
                        │
                        ▼
                ┌──────────────┐      ┌─────────────────┐
                │ MessageService│─────▶│    messages     │
                │ - saveUser    │      │ (保存用户消息)  │
                │ - saveAssistant│      │ (保存助手响应)  │
                └──────────────┘      └─────────────────┘
                        │
                        ▼
                ┌──────────────┐
                │ Spring AI    │
                │ ChatClient   │
                │ (流式生成)   │
                └──────────────┘
```

---

## 🎉 完成状态

| 任务                          | 状态 | 说明                              |
|-------------------------------|------|-----------------------------------|
| Task 1: 数据库迁移             | ✅   | Flyway 已执行，表已创建            |
| Task 2: FAQ 知识库             | ✅   | 实体、Mapper、工具、种子数据       |
| Task 3: 二次确认基础设施       | ✅   | 注解、拦截器、异常、Token 防重放   |
| Task 4: 对话持久化             | ✅   | 实体、服务类、控制器集成           |
| Task 5: 验证码服务             | ⛔   | **用户明确要求跳过此任务**         |

---

## 📝 后续建议

### 1. 前端开发

**二次确认弹窗**
```vue
<el-dialog
  v-model="showConfirmation"
  title="确认操作"
  width="400px"
>
  <p>{{ confirmationMessage }}</p>
  <template #footer>
    <el-button @click="showConfirmation = false">取消</el-button>
    <el-button type="danger" @click="confirmAction">确认</el-button>
  </template>
</el-dialog>
```

**SSE 事件处理**
```javascript
eventSource.addEventListener('confirmation_required', (e) => {
  const data = JSON.parse(e.data);
  showConfirmation.value = true;
  confirmationMessage.value = data.message;
  confirmationToken.value = data.confirmationToken;
});
```

### 2. 对话历史查询接口

```java
@GetMapping("/conversations/{userId}")
public ApiResponse<List<Conversation>> getUserConversations(@PathVariable String userId) {
    // 查询用户所有会话
}

@GetMapping("/conversations/{conversationId}/messages")
public ApiResponse<List<Message>> getMessages(@PathVariable Long conversationId) {
    // 查询会话消息历史
}
```

### 3. Token 清理任务

**定期清理过期 Token**
```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
public void cleanExpiredTokens() {
    LambdaQueryWrapper<UsedConfirmationToken> wrapper = new LambdaQueryWrapper<>();
    wrapper.lt(UsedConfirmationToken::getExpiresAt, LocalDateTime.now());
    usedTokenMapper.delete(wrapper);
}
```

### 4. 用户认证集成

**JWT 认证集成**
```java
// 从 Security Context 获取真实用户ID
String userId = SecurityContextHolder.getContext().getAuthentication().getName();
Long conversationId = conversationService.getOrCreateConversation(userId);
```

---

## 🔗 相关文档

- [AI_ARCHITECTURE.md](docs/AI_ARCHITECTURE.md) - AI 功能架构设计
- [AI_QUICK_START.md](docs/AI_QUICK_START.md) - AI 功能快速开始
- [QUICK_START.md](QUICK_START.md) - 项目快速开始
- [DATABASE_SETUP.md](DATABASE_SETUP.md) - 数据库配置

---

**文档版本**: 1.0  
**更新时间**: 2026-10-24  
**维护人**: 开发团队
