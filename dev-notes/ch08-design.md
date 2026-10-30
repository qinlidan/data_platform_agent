# 工具调用体系完善 - 技术设计文档

## 1. 需求分析

### 核心功能
1. **参数校验**：JSON Schema 自动校验，失败时清晰提示
2. **权限控制**：声明式权限规则，支持用户级 + 角色级
3. **审计留痕**：全量记录（包括拦截事件）
4. **超时重试**：智能区分读写，避免重复执行
5. **人工审批**：写操作二次确认，JWT token 防重放

### 验收标准
- 新工具零配置获得治理能力
- Agent 能追问补全参数
- 审批流程完整（确认/取消都有记录）
- 超时有兜底且不误伤写操作

## 2. 技术架构

### 2.1 装饰器链设计

```
ToolCallback (原始)
    ↓
AuditToolCallbackDecorator (最外层 - 记录一切)
    ↓
PermissionToolCallbackDecorator (权限校验)
    ↓
ConfirmationToolCallbackDecorator (二次确认)
    ↓
ValidationToolCallbackDecorator (参数校验)
    ↓
TimeoutRetryToolCallbackDecorator (超时重试)
    ↓
原始工具执行
```

**设计要点**：
- AuditDecorator 在最外层，能捕获所有拦截事件
- PermissionDecorator 在 ConfirmationDecorator 之前（先验权限，再确认）
- ValidationDecorator 在倒数第二层（最接近工具执行）
- 每个 Decorator 只做一件事，职责清晰

### 2.2 核心组件

#### 2.2.1 ToolCallbackDecorator (抽象基类)
```java
public abstract class ToolCallbackDecorator implements ToolCallback {
    protected final ToolCallback delegate;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return decorateCall(toolInput, toolContext);
    }
    
    protected abstract String decorateCall(String toolInput, ToolContext toolContext);
}
```

#### 2.2.2 ToolCallbackDecoratorFactory
```java
@Component
public class ToolCallbackDecoratorFactory {
    public ToolCallback decorate(ToolCallback original) {
        ToolCallback decorated = original;
        decorated = new TimeoutRetryToolCallbackDecorator(decorated, ...);
        decorated = new ValidationToolCallbackDecorator(decorated, ...);
        decorated = new ConfirmationToolCallbackDecorator(decorated, ...);
        decorated = new PermissionToolCallbackDecorator(decorated, ...);
        decorated = new AuditToolCallbackDecorator(decorated, ...);
        return decorated;
    }
}
```

#### 2.2.3 ToolContext 扩展
Spring AI 的 ToolContext 是 `Map<String, Object>` 的包装，我们约定以下 key：
- `userId`: String
- `roles`: List<String>
- `conversationId`: String（用于审计关联）
- `confirmationToken`: String（二次确认 token）

### 2.3 参数校验实现

```java
public class ValidationToolCallbackDecorator extends ToolCallbackDecorator {
    private final JsonSchemaFactory schemaFactory = 
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    
    @Override
    protected String decorateCall(String toolInput, ToolContext toolContext) {
        // 1. 获取 JSON Schema
        String schemaJson = delegate.getToolDefinition().inputSchema();
        JsonSchema schema = schemaFactory.getSchema(schemaJson);
        
        // 2. 校验输入
        Set<ValidationMessage> errors = schema.validate(new ObjectMapper().readTree(toolInput));
        if (!errors.isEmpty()) {
            throw new ToolParameterValidationException(
                buildUserFriendlyMessage(errors)
            );
        }
        
        // 3. 通过校验，继续执行
        return delegate.call(toolInput, toolContext);
    }
}
```

### 2.4 权限控制实现

```java
public class PermissionToolCallbackDecorator extends ToolCallbackDecorator {
    private final ToolPermissionRule permissionRule;
    
    @Override
    protected String decorateCall(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        String userId = (String) toolContext.getContext().get("userId");
        List<String> roles = (List<String>) toolContext.getContext().get("roles");
        
        if (!permissionRule.hasPermission(toolName, userId, roles)) {
            throw new ToolPermissionDeniedException(
                "User " + userId + " has no permission to call tool: " + toolName
            );
        }
        
        return delegate.call(toolInput, toolContext);
    }
}
```

**ToolPermissionRule 设计**：
```java
@Component
public class ToolPermissionRule {
    // 工具名 -> 允许的角色列表
    private final Map<String, Set<String>> toolRoleMap;
    
    public boolean hasPermission(String toolName, String userId, List<String> roles) {
        // 1. 检查是否需要权限控制
        if (!toolRoleMap.containsKey(toolName)) {
            return true; // 默认允许
        }
        
        // 2. 检查角色
        Set<String> allowedRoles = toolRoleMap.get(toolName);
        return roles.stream().anyMatch(allowedRoles::contains);
    }
}
```

### 2.5 二次确认实现

```java
public class ConfirmationToolCallbackDecorator extends ToolCallbackDecorator {
    private final ConfirmationTokenService tokenService;
    private final Set<String> toolsRequireConfirmation;
    
    @Override
    protected String decorateCall(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        
        // 1. 检查是否需要确认
        if (!toolsRequireConfirmation.contains(toolName)) {
            return delegate.call(toolInput, toolContext);
        }
        
        // 2. 检查是否已确认
        String token = (String) toolContext.getContext().get("confirmationToken");
        if (token != null && tokenService.verify(token, toolName, toolInput)) {
            // 已确认，执行
            return delegate.call(toolInput, toolContext);
        }
        
        // 3. 未确认，生成 token 并抛出异常
        String newToken = tokenService.generate(toolName, toolInput);
        throw new ToolNeedConfirmationException(
            "Tool " + toolName + " requires user confirmation",
            newToken,
            toolInput
        );
    }
}
```

**ConfirmationTokenService 设计**：
```java
@Service
public class ConfirmationTokenService {
    private final JwtEncoder jwtEncoder;
    
    public String generate(String toolName, String toolInput) {
        // JWT Payload: {toolName, paramsHash, exp}
        String paramsHash = DigestUtils.sha256Hex(toolInput);
        return Jwts.builder()
            .claim("toolName", toolName)
            .claim("paramsHash", paramsHash)
            .expiration(Date.from(Instant.now().plusSeconds(300))) // 5分钟
            .signWith(secretKey)
            .compact();
    }
    
    public boolean verify(String token, String toolName, String toolInput) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            
            return toolName.equals(claims.get("toolName")) &&
                   DigestUtils.sha256Hex(toolInput).equals(claims.get("paramsHash"));
        } catch (JwtException e) {
            return false;
        }
    }
}
```

### 2.6 超时重试实现

```java
public class TimeoutRetryToolCallbackDecorator extends ToolCallbackDecorator {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Duration timeout = Duration.ofSeconds(30);
    
    @Override
    protected String decorateCall(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        boolean isWriteOp = isWriteOperation(toolName);
        
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> delegate.call(toolInput, toolContext),
            executor
        );
        
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ToolExecutionTimeoutException(
                "Tool " + toolName + " execution timeout after " + timeout
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            
            // 只对网络抖动类错误重试，且不重试写操作
            if (!isWriteOp && isRetryableException(cause)) {
                return retryOnce(toolInput, toolContext);
            }
            
            throw new ToolExecutionException("Tool execution failed", cause);
        }
    }
    
    private boolean isWriteOperation(String toolName) {
        // 根据工具名判断（简单规则：包含 create/update/delete/publish）
        return toolName.matches(".*(create|update|delete|publish|remove).*");
    }
    
    private boolean isRetryableException(Throwable e) {
        return e instanceof SocketTimeoutException ||
               e instanceof ConnectException ||
               e instanceof HttpRetryException;
    }
}
```

### 2.7 审计实现

```java
@Component
public class AuditToolCallbackDecorator extends ToolCallbackDecorator {
    private final ToolAuditService auditService;
    
    @Override
    protected String decorateCall(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        String conversationId = (String) toolContext.getContext().get("conversationId");
        
        ToolAuditLog log = new ToolAuditLog();
        log.setConversationId(conversationId);
        log.setToolName(toolName);
        log.setToolInput(toolInput);
        log.setStartTime(System.currentTimeMillis());
        
        try {
            String result = delegate.call(toolInput, toolContext);
            log.setStatus("SUCCESS");
            log.setToolResult(truncate(result, 1000)); // 截断避免过长
            return result;
        } catch (ToolPermissionDeniedException e) {
            log.setStatus("PERMISSION_DENIED");
            log.setErrorMessage(e.getMessage());
            throw e;
        } catch (ToolParameterValidationException e) {
            log.setStatus("VALIDATION_FAILED");
            log.setErrorMessage(e.getMessage());
            throw e;
        } catch (ToolNeedConfirmationException e) {
            log.setStatus("NEED_CONFIRMATION");
            log.setErrorMessage("Waiting for user confirmation");
            throw e;
        } catch (ToolExecutionTimeoutException e) {
            log.setStatus("TIMEOUT");
            log.setErrorMessage(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            log.setEndTime(System.currentTimeMillis());
            log.setDuration(log.getEndTime() - log.getStartTime());
            auditService.saveAsync(log); // 异步保存
        }
    }
}

@Service
public class ToolAuditService {
    @Async
    public void saveAsync(ToolAuditLog log) {
        try {
            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不影响业务，只记日志
            logger.error("Failed to save audit log", e);
        }
    }
}
```

### 2.8 数据库表设计

```sql
CREATE TABLE tool_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    tool_call_id VARCHAR(64) COMMENT '工具调用ID（来自模型响应）',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    tool_source VARCHAR(32) NOT NULL DEFAULT 'INTERNAL' COMMENT '工具来源：INTERNAL/MCP',
    tool_input TEXT COMMENT '调用参数（JSON）',
    tool_result TEXT COMMENT '返回结果（截断）',
    status VARCHAR(32) NOT NULL COMMENT '状态：SUCCESS/FAILED/TIMEOUT/VALIDATION_FAILED/PERMISSION_DENIED/NEED_CONFIRMATION',
    error_message VARCHAR(512) COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    duration BIGINT COMMENT '耗时（毫秒）',
    user_id VARCHAR(64) COMMENT '用户ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id),
    INDEX idx_user_time (user_id, create_time)
) COMMENT '工具调用审计日志';
```

### 2.9 异常处理与模型回灌

```java
@ControllerAdvice
public class ToolExecutionExceptionHandler {
    
    @ExceptionHandler(ToolParameterValidationException.class)
    public ResponseEntity<String> handleValidation(ToolParameterValidationException e) {
        // 返回给前端 SSE 事件
        return ResponseEntity.ok(toSSE(Map.of(
            "type", "tool_error",
            "category", "parameter_validation",
            "message", e.getUserFriendlyMessage()
        )));
    }
    
    @ExceptionHandler(ToolNeedConfirmationException.class)
    public ResponseEntity<String> handleConfirmation(ToolNeedConfirmationException e) {
        return ResponseEntity.ok(toSSE(Map.of(
            "type", "confirmation_required",
            "token", e.getToken(),
            "toolName", e.getToolName(),
            "toolInput", e.getToolInput()
        )));
    }
    
    @ExceptionHandler(ToolPermissionDeniedException.class)
    public ResponseEntity<String> handlePermission(ToolPermissionDeniedException e) {
        return ResponseEntity.ok(toSSE(Map.of(
            "type", "tool_error",
            "category", "permission_denied",
            "message", e.getMessage()
        )));
    }
}
```

## 3. 集成方式

### 3.1 自动装配

```java
@Configuration
public class ToolCallbackAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ToolCallbackDecoratorFactory toolCallbackDecoratorFactory(
        ToolAuditService auditService,
        ToolPermissionRule permissionRule,
        ConfirmationTokenService tokenService,
        ToolConfirmationConfig confirmationConfig
    ) {
        return new ToolCallbackDecoratorFactory(
            auditService, permissionRule, tokenService, confirmationConfig
        );
    }
    
    @Bean
    public BeanPostProcessor toolCallbackDecoratorProcessor(
        ToolCallbackDecoratorFactory factory
    ) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ToolCallback) {
                    return factory.decorate((ToolCallback) bean);
                }
                return bean;
            }
        };
    }
}
```

### 3.2 使用示例

```java
// 开发者只需写业务逻辑
@Component
public class JobTools {
    
    @Tool(description = "发布作业到生产环境")
    public String publishJob(
        @ToolParam(description = "作业ID", required = true) String jobId
    ) {
        // 业务逻辑
        jobService.publish(jobId);
        return "作业 " + jobId + " 发布成功";
    }
}

// 自动获得：
// - 参数校验（jobId 必填）
// - 权限校验（需要 ADMIN 角色）
// - 二次确认（前端弹框）
// - 超时保护（30秒）
// - 审计记录（全量）
```

## 4. 配置示例

```yaml
tool:
  governance:
    # 权限规则
    permission:
      publishJob: [ADMIN, OPERATOR]
      deleteJob: [ADMIN]
      
    # 需要确认的工具
    confirmation:
      enabled: true
      tools: [publishJob, deleteJob]
      token-ttl: 300 # 5分钟
      
    # 超时配置
    timeout:
      default: 30s
      per-tool:
        queryLargeData: 60s
        
    # 重试配置
    retry:
      enabled: true
      max-attempts: 2
      exclude-write-ops: true
```

## 5. 任务分解

1. **数据库表 & 实体类**（tool_audit_logs）
2. **核心装饰器抽象**（ToolCallbackDecorator）
3. **参数校验装饰器**（ValidationToolCallbackDecorator）
4. **权限控制装饰器**（PermissionToolCallbackDecorator）
5. **二次确认装饰器**（ConfirmationToolCallbackDecorator + ConfirmationTokenService）
6. **超时重试装饰器**（TimeoutRetryToolCallbackDecorator）
7. **审计装饰器**（AuditToolCallbackDecorator + ToolAuditService）
8. **装饰器工厂**（ToolCallbackDecoratorFactory）
9. **自动装配**（BeanPostProcessor）
10. **异常处理器**（ToolExecutionExceptionHandler）
11. **配置类**（ToolGovernanceProperties）
12. **集成测试**（验收标准）

## 6. 风险与限制

### 风险
1. **装饰器链性能开销**：每次调用都要经过多层装饰器
   - 缓解：审计异步化，校验用缓存
2. **JWT token 管理**：需要 secret key 管理
   - 缓解：从 Spring Security 复用现有密钥
3. **超时判断准确性**：isWriteOperation() 基于工具名规则，可能误判
   - 缓解：提供显式配置覆盖

### 限制
1. 不支持工具级别的细粒度超时配置（除非扩展 ToolMetadata）
2. 重试逻辑简单（只重试一次），复杂场景需引入 Resilience4j
3. 审计日志没有分析查询接口（只记录不分析）

## 7. 后续优化方向

1. **工具级配置**：通过自定义注解 `@ToolGovernance(timeout=60, retryable=false)` 精细化控制
2. **审计分析**：提供审计日志查询 API，支持按用户、工具、时间范围统计
3. **动态权限规则**：从数据库加载权限配置，支持运行时更新
4. **MCP 工具集成**：扩展装饰器链支持外部 MCP 工具
