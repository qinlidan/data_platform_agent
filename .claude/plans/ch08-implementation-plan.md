# 工具调用体系完善 - 实施计划

## 1. 现状分析

### 1.1 当前架构
- **工具定义**：使用 Spring AI 1.0.0 标准注解（`@Tool` + `@ToolParam`）
- **工具注册**：通过 `ToolCallbacks.from()` 自动转换为 `ToolCallback`
- **工具包装**：`ToolMonitorConfig.wrapTools()` 手动包装为 `ObservableToolCallback`
- **已有能力**：
  - 基础参数校验（ObservableToolCallback 中简单的 null/format 检查）
  - UI 事件发射（tool_start/tool_end）
  - 二次确认服务（ConfirmationService + JWT token）
  - 简单的审计日志（AuditDecorator，只打日志，不持久化）

### 1.2 存在的问题
1. **装饰器体系不完整**：
   - ValidationDecorator 使用硬编码 switch-case，不通用
   - 缺少 PermissionDecorator 和 TimeoutRetryDecorator
   - 审计没有数据库持久化
2. **装饰器没有自动组合**：各装饰器独立，没有形成统一的链
3. **二次确认机制未集成**：`@RequireConfirmation` 注解存在但未生效
4. **参数校验不基于 JSON Schema**：无法自动根据 ToolDefinition.inputSchema() 校验

## 2. 设计方案

### 2.1 架构调整

**保持现有模式，在 ObservableToolCallback 内部组织装饰器链**：

```
ToolCallback (原始，来自 ToolCallbacks.from(jobTool))
    ↓
ObservableToolCallback (包装器)
    ├─ AuditInterceptor (最外层，记录一切)
    ├─ PermissionInterceptor (权限校验)
    ├─ ConfirmationInterceptor (二次确认)
    ├─ ValidationInterceptor (JSON Schema 校验)
    ├─ TimeoutRetryInterceptor (超时重试)
    └─ 调用 delegate.call()
```

**关键设计点**：
1. 不引入新的 Decorator 抽象类，而是在 `ObservableToolCallback.call()` 中顺序执行各 Interceptor
2. 每个 Interceptor 是独立的 Spring Bean，通过构造器注入到 `ObservableToolCallback`
3. 使用 `ToolExecutionContext` 对象在各 Interceptor 间传递上下文（userId、roles、toolName 等）

### 1.2 工具治理装饰器链

```java
原始ToolCallback
    ↓
AuditToolCallback（最外层）
    ↓
ValidationToolCallback（JSON Schema校验）
    ↓
PermissionToolCallback（权限校验）
    ↓
RetryToolCallback（重试逻辑）
    ↓
TimeoutToolCallback（超时控制）
    ↓
FormattingToolCallback（结果格式化）
    ↓
原始工具执行

### 2.2 核心组件

#### 2.2.1 ToolExecutionContext (新增)
```java
public class ToolExecutionContext {
    private String toolName;
    private String toolInput;
    private String userId;
    private List<String> roles;
    private Long conversationId;
    private String confirmationToken; // 二次确认 token
    
    // Getters & Setters
}
```

#### 2.2.2 ToolInterceptor 接口 (新增)
```java
public interface ToolInterceptor {
    /**
     * 拦截器执行
     * @param context 执行上下文
     * @param next 调用链的下一步
     * @return 执行结果
     * @throws Exception 拦截失败（参数校验、权限拒绝等）
     */
    String intercept(ToolExecutionContext context, InterceptorChain next) throws Exception;
}

@FunctionalInterface
public interface InterceptorChain {
    String proceed(ToolExecutionContext context) throws Exception;
}
```

#### 2.2.3 重构 ObservableToolCallback
```java
public class ObservableToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    private final Sinks.Many<Map<String, Object>> eventSink;
    private final List<ToolInterceptor> interceptors; // 拦截器链
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 构建执行上下文
        ToolExecutionContext context = buildContext(toolInput, toolContext);
        
        // 执行拦截器链
        return executeChain(context, 0);
    }
    
    private String executeChain(ToolExecutionContext context, int index) {
        if (index == interceptors.size()) {
            // 链末端，调用原始工具
            return delegate.call(context.getToolInput(), buildToolContext(context));
        }
        
        ToolInterceptor interceptor = interceptors.get(index);
        return interceptor.intercept(context, ctx -> executeChain(ctx, index + 1));
    }
}
```

### 2.3 各 Interceptor 实现

#### 2.3.1 AuditInterceptor (审计拦截器)
**职责**：记录工具调用全程（包括拦截事件），异步持久化到数据库

**实现要点**：
- 在最外层执行（index=0），能捕获所有异常
- 记录：toolName、toolInput、toolResult、status、duration、userId
- 异步保存到 `tool_audit_logs` 表
- 区分状态：SUCCESS / VALIDATION_FAILED / PERMISSION_DENIED / NEED_CONFIRMATION / TIMEOUT / FAILED

#### 2.3.2 PermissionInterceptor (权限拦截器)
**职责**：根据工具名和用户角色判断是否有权限调用

**实现要点**：
- 从配置文件读取权限规则：`tool.governance.permission.{toolName}: [ADMIN, OPERATOR]`
- 校验失败抛出 `ToolPermissionDeniedException`
- 默认策略：未配置的工具允许所有人调用

#### 2.3.3 ConfirmationInterceptor (二次确认拦截器)
**职责**：对标记 `@RequireConfirmation` 的工具进行二次确认

**实现要点**：
- 从 ToolDefinition.metadata() 或工具方法注解读取是否需要确认
- 检查 context.confirmationToken 是否有效（调用 ConfirmationService）
- 未确认时生成 token，抛出 `NeedConfirmationException`（已存在）
- 已确认时放行，继续链

**注意**：`@RequireConfirmation` 注解的元数据需要能被 ToolDefinition 携带，或者通过工具名匹配配置文件规则

#### 2.3.4 ValidationInterceptor (参数校验拦截器)
**职责**：基于 JSON Schema 自动校验参数

**实现要点**：
- 获取 `delegate.getToolDefinition().inputSchema()`（JSON Schema 字符串）
- 使用 `networknt/json-schema-validator` 校验 toolInput
- 校验失败抛出 `ToolParameterValidationException`，包含用户友好的错误信息
- 生成友好提示：
  ```
  参数校验失败：
  - jobCode: 必填字段不能为空
  - version: 必须是正整数
  请补充完整参数后重试。
  ```

#### 2.3.5 TimeoutRetryInterceptor (超时重试拦截器)
**职责**：为工具调用加入超时保护，网络抖动时自动重试

**实现要点**：
- 使用 `CompletableFuture` + `get(timeout, unit)` 实现超时
- 默认超时：30秒（可配置）
- 超时后抛出 `ToolExecutionTimeoutException`
- 判断是否为写操作（工具名包含 create/update/delete/publish）：
  - 写操作：不重试
  - 读操作 + 网络类异常：重试一次
- 可重试的异常类型：SocketTimeoutException、ConnectException

## 3. 数据库表设计

### 3.1 工具审计日志表
```sql
CREATE TABLE tool_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NULL COMMENT '会话ID（如果来自AI对话）',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    tool_input TEXT COMMENT '调用参数（JSON）',
    tool_result TEXT COMMENT '返回结果（截断至1000字符）',
    status VARCHAR(32) NOT NULL COMMENT '状态：SUCCESS/FAILED/TIMEOUT/VALIDATION_FAILED/PERMISSION_DENIED/NEED_CONFIRMATION',
    error_message VARCHAR(512) COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    duration BIGINT COMMENT '耗时（毫秒）',
    user_id VARCHAR(64) COMMENT '用户ID',
    user_roles JSON COMMENT '用户角色列表',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_conversation (conversation_id),
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_tool_status (tool_name, status)
) COMMENT '工具调用审计日志';
```

## 4. 配置设计

### 4.1 YAML 配置（application.yml）
```yaml
tool:
  governance:
    # 权限规则
    permission:
      deleteJob: [ADMIN]
      publishJob: [ADMIN, OPERATOR]
      deleteDataSource: [ADMIN]
      
    # 需要二次确认的工具
    confirmation:
      enabled: true
      tools: [deleteJob, publishJob, deleteDataSource]
      
    # 超时配置
    timeout:
      default: 30000 # 默认30秒
      per-tool:
        executeSQL: 60000 # 单独配置某工具
        
    # 重试配置
    retry:
      enabled: true
      max-attempts: 1 # 最多重试1次
```

### 4.2 配置类
```java
@Configuration
@ConfigurationProperties(prefix = "tool.governance")
@Data
public class ToolGovernanceProperties {
    private Map<String, List<String>> permission = new HashMap<>();
    private ConfirmationConfig confirmation = new ConfirmationConfig();
    private TimeoutConfig timeout = new TimeoutConfig();
    private RetryConfig retry = new RetryConfig();
    
    @Data
    public static class ConfirmationConfig {
        private boolean enabled = true;
        private List<String> tools = new ArrayList<>();
    }
    
    @Data
    public static class TimeoutConfig {
        private long defaultTimeout = 30000;
        private Map<String, Long> perTool = new HashMap<>();
    }
    
    @Data
    public static class RetryConfig {
        private boolean enabled = true;
        private int maxAttempts = 1;
    }
}
```

## 5. 异常处理与 SSE 集成

### 5.1 自定义异常类（部分已存在）
```java
// 已存在
public class NeedConfirmationException extends RuntimeException {
    private String operation;
    private String riskLevel;
    private String confirmationToken;
}

// 新增
public class ToolParameterValidationException extends RuntimeException {
    private List<ValidationError> errors;
}

public class ToolPermissionDeniedException extends RuntimeException {
    private String toolName;
    private String userId;
}

public class ToolExecutionTimeoutException extends RuntimeException {
    private String toolName;
    private long timeoutMs;
}
```

### 5.2 Controller 异常处理（已有部分逻辑）
在 `AiAssistantController.chatStream()` 中已有 `NeedConfirmationException` 的捕获，需要补充其他异常类型：

```java
.onErrorResume(e -> {
    if (e instanceof NeedConfirmationException) {
        // 已有逻辑
    } else if (e instanceof ToolParameterValidationException) {
        return Flux.just(ServerSentEvent.builder()
            .event("validation_error")
            .data(Map.of("message", e.getMessage(), "errors", e.getErrors()))
            .build());
    } else if (e instanceof ToolPermissionDeniedException) {
        return Flux.just(ServerSentEvent.builder()
            .event("permission_denied")
            .data(Map.of("message", e.getMessage()))
            .build());
    }
    // ... 其他异常
})
```

## 6. 实施步骤

### 阶段1：基础设施（数据库、配置、异常）
1. **创建数据库迁移文件**：`V1.4__create_tool_audit_logs.sql`
2. **创建实体类和 Mapper**：`ToolAuditLog` + `ToolAuditLogMapper`
3. **创建配置类**：`ToolGovernanceProperties`
4. **创建异常类**：`ToolParameterValidationException`、`ToolPermissionDeniedException`、`ToolExecutionTimeoutException`
5. **创建上下文类**：`ToolExecutionContext`

### 阶段2：拦截器实现
6. **创建拦截器接口**：`ToolInterceptor` + `InterceptorChain`
7. **实现 AuditInterceptor**：审计日志持久化
8. **实现 PermissionInterceptor**：权限校验
9. **实现 ConfirmationInterceptor**：二次确认集成
10. **实现 ValidationInterceptor**：JSON Schema 校验（依赖 networknt/json-schema-validator）
11. **实现 TimeoutRetryInterceptor**：超时重试

### 阶段3：集成与配置
12. **重构 ObservableToolCallback**：集成拦截器链
13. **更新 ToolMonitorConfig**：自动装配拦截器
14. **更新 application.yml**：添加治理配置
15. **更新 Controller 异常处理**：补充新异常类型的 SSE 事件

### 阶段4：测试验证
16. **单元测试**：各 Interceptor 独立测试
17. **集成测试**：完整的工具调用链路测试
18. **验收测试**：
    - 参数校验失败，Agent 能追问
    - 权限拒绝，返回清晰提示
    - 二次确认流程完整（确认/取消都有记录）
    - 超时重试正确触发
    - 审计日志完整记录（包括拦截事件）

## 7. 风险与限制

### 7.1 风险
1. **JSON Schema 获取问题**：Spring AI 1.0.0 的 `ToolDefinition.inputSchema()` 可能返回空或简单 schema，需验证
   - 缓解：如果 schema 不可用，退化为基础的 null 检查
2. **@RequireConfirmation 注解无法传递到 ToolDefinition**：
   - 缓解：改用配置文件 `tool.governance.confirmation.tools` 列表匹配工具名
3. **拦截器链性能开销**：每次调用都要经过多层拦截
   - 缓解：审计异步化，校验用缓存

### 7.2 限制
1. 不支持工具级别的细粒度超时配置（除非在 YAML 中单独配置）
2. 重试逻辑简单（只重试一次），复杂场景需引入 Resilience4j
3. 权限规则是静态配置，不支持运行时动态调整

## 8. 后续优化方向
1. **动态权限规则**：从数据库加载，支持前端管理界面
2. **审计分析 API**：提供审计日志查询接口，按工具/用户/时间统计
3. **拦截器插件化**：支持用户自定义拦截器
4. **MCP 工具集成**：扩展拦截器链支持外部 MCP 工具
