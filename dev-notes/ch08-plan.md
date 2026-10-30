# 第8章实施计划：工具调用体系改造

## 一、总体架构设计

### 1.1 核心架构调整

**从硬编码调用链 → StateGraph 编排**

```
当前架构：
Controller → ChatClient.call() → 工具自动调用 → 返回结果

目标架构：
Controller → StateGraph.stream() → Agent节点 → Tool节点 → 审批节点 → 返回流
                                      ↓           ↓           ↓
                                   大模型推理   工具执行    人工确认
```

**关键设计点**：
- **Agent节点**：调用 ChatClient 进行推理，生成工具调用决策
- **Tool节点**：执行工具，通过装饰器链实现治理（校验、权限、审计、超时、重试）
- **Approval节点**：写操作触发 interrupt，等待人工确认
- **流式输出适配**：从 NodeOutput 提取 Token 增量 + 自定义事件（表格、工具调用）

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
```

### 1.3 数据流设计

**输入流**：
```
用户消息 → StateGraph input → OverAllState
   ↓
State包含：
  - input: 用户消息
  - messages: 历史消息列表
  - tool_calls: 待执行的工具调用列表
  - approval_pending: 待审批标记
  - checkpoint_id: 断点ID（用于resume）
```

**输出流**：
```
CompiledGraph.stream() → Flux<NodeOutput>
   ↓
NodeOutput types:
  - TEXT_CHUNK: Token增量（从Agent节点的ChatResponse提取）
  - CUSTOM_EVENT: 自定义事件（表格、工具调用）
  - INTERRUPT: 审批中断
  - COMPLETED: 完成
```

---

## 二、技术方案详解

### 2.1 StateGraph构建

#### 2.1.1 状态定义

```java
public class AgentState {
    private String input;              // 用户输入
    private List<Message> messages;    // 历史消息
    private List<ToolCall> toolCalls;  // 待执行工具调用
    private Map<String, String> toolResults; // 工具执行结果
    private Boolean approvalPending;   // 是否等待审批
    private String checkpointId;       // 断点ID
    private ToolContext toolContext;   // 工具上下文（userId, roles）
}
```

#### 2.1.2 节点设计

**Agent节点（AgentNode）**：
```java
public class AgentNode implements AsyncNodeAction {
    @Override
    public CompletableFuture<NodeOutput> apply(OverAllState state, RunnableConfig config) {
        // 1. 获取用户消息和历史
        // 2. 调用 ChatClient.stream().call()
        // 3. 解析模型响应，提取工具调用
        // 4. 更新状态：toolCalls、messages
        // 5. 返回 NodeOutput（包含文本流 + 工具调用决策）
    }
}
```

**Tool节点（ToolNode）**：
```java
public class ToolNode implements AsyncNodeAction {
    @Override
    public CompletableFuture<NodeOutput> apply(OverAllState state, RunnableConfig config) {
        // 1. 从状态获取 toolCalls
        // 2. 遍历每个工具调用
        // 3. 检查是否需要审批（写操作）
        //    - 需要审批：触发 interrupt，返回 INTERRUPT NodeOutput
        //    - 不需要：直接执行
        // 4. 通过装饰器链执行工具
        // 5. 收集结果，更新状态：toolResults
        // 6. 返回 NodeOutput（工具执行结果）
    }
}
```

**Approval节点（ApprovalNode）**：
```java
// 使用 StateGraph 的 Checkpoint 能力
// 在 ToolNode 中检测到需要审批时，调用：
state.update("approvalPending", true);
// 返回特殊的 NodeOutput，触发前端展示审批卡片
// 用户确认后，前端调用：
compiledGraph.resume(checkpointId, updatedState, config);
```

#### 2.1.3 图结构

```java
@Bean
public StateGraph agentGraph(ChatClient chatClient) {
    OverAllStateFactory stateFactory = () -> {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy("input", new ReplaceStrategy());
        state.registerKeyAndStrategy("messages", new AppendStrategy());
        state.registerKeyAndStrategy("toolCalls", new ReplaceStrategy());
        state.registerKeyAndStrategy("toolResults", new ReplaceStrategy());
        state.registerKeyAndStrategy("approvalPending", new ReplaceStrategy());
        state.registerKeyAndStrategy("toolContext", new ReplaceStrategy());
        return state;
    };

    AgentNode agentNode = new AgentNode(chatClient, toolCallbackProvider);
    ToolNode toolNode = new ToolNode(toolCallbackProvider, auditService);

    return new StateGraph("DataPlatformAgent", stateFactory)
        .addNode("agent", node_async(agentNode))
        .addNode("tools", node_async(toolNode))
        .addEdge(START, "agent")
        .addConditionalEdges("agent", 
            // 根据是否有工具调用决定下一步
            (state, config) -> state.value("toolCalls").isPresent() ? "tools" : END)
        .addConditionalEdges("tools",
            // 根据是否需要审批决定下一步
            (state, config) -> state.value("approvalPending").orElse(false) ? INTERRUPT : "agent")
        .addEdge("agent", END); // 无工具调用时直接结束
}
```

### 2.2 装饰器链实现

#### 2.2.1 基础装饰器接口

```java
public abstract class ToolCallbackDecorator implements ToolCallback {
    protected final ToolCallback delegate;
    
    public ToolCallbackDecorator(ToolCallback delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }
    
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }
    
    @Override
    public abstract String call(String toolInput, ToolContext toolContext);
}
```

#### 2.2.2 审计装饰器（最外层）

```java
@Component
public class AuditToolCallback extends ToolCallbackDecorator {
    private final ToolAuditService auditService;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        String toolCallId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        ToolAuditLog auditLog = ToolAuditLog.builder()
            .toolCallId(toolCallId)
            .toolName(toolName)
            .source("INTERNAL") // 或 "MCP_SERVER_NAME"
            .arguments(toolInput)
            .conversationId(extractConversationId(toolContext))
            .userId(extractUserId(toolContext))
            .status("PENDING")
            .build();
        
        try {
            String result = delegate.call(toolInput, toolContext);
            
            auditLog.setStatus("SUCCESS");
            auditLog.setResultSummary(truncate(result, 500));
            auditLog.setDuration(System.currentTimeMillis() - startTime);
            
            return result;
            
        } catch (ValidationException e) {
            auditLog.setStatus("VALIDATION_FAILED");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } catch (PermissionDeniedException e) {
            auditLog.setStatus("PERMISSION_DENIED");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } catch (TimeoutException e) {
            auditLog.setStatus("TIMEOUT");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } catch (Exception e) {
            auditLog.setStatus("FAILED");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            auditLog.setDuration(System.currentTimeMillis() - startTime);
            // 异步保存审计日志
            auditService.saveAuditLogAsync(auditLog);
        }
    }
}
```

#### 2.2.3 参数校验装饰器

```java
@Component
public class ValidationToolCallback extends ToolCallbackDecorator {
    private final JsonSchemaFactory schemaFactory;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        String inputSchema = getToolDefinition().inputSchema();
        
        if (inputSchema == null || inputSchema.isEmpty()) {
            // 无schema，跳过校验
            return delegate.call(toolInput, toolContext);
        }
        
        try {
            JsonSchema schema = schemaFactory.getSchema(inputSchema);
            JsonNode inputNode = objectMapper.readTree(toolInput);
            
            Set<ValidationMessage> errors = schema.validate(inputNode);
            
            if (!errors.isEmpty()) {
                String errorMsg = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
                throw new ValidationException("参数校验失败: " + errorMsg);
            }
            
            return delegate.call(toolInput, toolContext);
            
        } catch (JsonProcessingException e) {
            throw new ValidationException("参数格式错误: " + e.getMessage());
        }
    }
}
```

#### 2.2.4 权限校验装饰器

```java
@Component
public class PermissionToolCallback extends ToolCallbackDecorator {
    private final ToolPermissionConfig permissionConfig;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        String userId = toolContext.getContext().get("userId");
        Set<String> roles = parseRoles(toolContext.getContext().get("roles"));
        
        // 检查权限配置
        if (!permissionConfig.hasPermission(toolName, userId, roles)) {
            throw new PermissionDeniedException(
                String.format("用户 %s 无权执行工具 %s", userId, toolName)
            );
        }
        
        return delegate.call(toolInput, toolContext);
    }
}
```

#### 2.2.5 超时装饰器

```java
@Component
public class TimeoutToolCallback extends ToolCallbackDecorator {
    private final ToolTimeoutConfig timeoutConfig;
    private final ExecutorService executorService;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        Duration timeout = timeoutConfig.getTimeout(toolName);
        
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> delegate.call(toolInput, toolContext),
            executorService
        );
        
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ToolTimeoutException(
                String.format("工具 %s 执行超时 (>%dms)", toolName, timeout.toMillis())
            );
        } catch (Exception e) {
            throw new RuntimeException("工具执行异常", e);
        }
    }
}
```

#### 2.2.6 重试装饰器

```java
@Component
public class RetryToolCallback extends ToolCallbackDecorator {
    private final ToolRetryConfig retryConfig;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        boolean isWriteOperation = retryConfig.isWriteOperation(toolName);
        int maxRetries = isWriteOperation ? 0 : retryConfig.getMaxRetries(toolName);
        
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount <= maxRetries) {
            try {
                return delegate.call(toolInput, toolContext);
            } catch (Exception e) {
                if (!isRetriable(e) || retryCount >= maxRetries) {
                    // 记录重试次数到 ToolContext（用于审计）
                    toolContext.getContext().put("retryCount", String.valueOf(retryCount));
                    throw e;
                }
                
                lastException = e;
                retryCount++;
                
                // 等待后重试
                try {
                    Thread.sleep(retryConfig.getRetryDelay(retryCount).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        
        throw new RuntimeException("重试失败", lastException);
    }
    
    private boolean isRetriable(Exception e) {
        return e instanceof SocketTimeoutException 
            || e instanceof ConnectException
            || e instanceof IOException;
    }
}
```

#### 2.2.7 结果格式化装饰器

```java
@Component
public class FormattingToolCallback extends ToolCallbackDecorator {
    private final ToolFormattingConfig formattingConfig;
    private final ObjectMapper objectMapper;
    
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        String toolName = getToolDefinition().name();
        
        try {
            // 1. 解析结果
            JsonNode resultNode = objectMapper.readTree(result);
            
            // 2. 按工具名裁剪字段
            Set<String> allowedFields = formattingConfig.getAllowedFields(toolName);
            if (allowedFields != null && !allowedFields.isEmpty()) {
                resultNode = filterFields(resultNode, allowedFields);
            }
            
            // 3. 翻译枚举码
            resultNode = translateEnums(resultNode, toolName);
            
            // 4. 确保中文不转义
            return objectMapper.writer()
                .without(JsonWriteFeature.ESCAPE_NON_ASCII)
                .writeValueAsString(resultNode);
                
        } catch (JsonProcessingException e) {
            // 如果不是JSON，直接返回
            return result;
        }
    }
}
```

### 2.3 装饰器链组装

```java
@Configuration
public class ToolGovernanceConfig {
    
    @Bean
    public ToolCallbackProvider governedToolCallbackProvider(
            List<ToolCallback> rawTools,
            AuditToolCallback auditDecorator,
            ValidationToolCallback validationDecorator,
            PermissionToolCallback permissionDecorator,
            RetryToolCallback retryDecorator,
            TimeoutToolCallback timeoutDecorator,
            FormattingToolCallback formattingDecorator
    ) {
        List<ToolCallback> governedTools = rawTools.stream()
            .map(tool -> {
                // 从内到外包装
                ToolCallback wrapped = tool;
                wrapped = new FormattingToolCallback(wrapped);
                wrapped = new TimeoutToolCallback(wrapped);
                wrapped = new RetryToolCallback(wrapped);
                wrapped = new PermissionToolCallback(wrapped);
                wrapped = new ValidationToolCallback(wrapped);
                wrapped = new AuditToolCallback(wrapped);
                return wrapped;
            })
            .collect(Collectors.toList());
        
        return ToolCallbackProvider.from(governedTools);
    }
}


### 2.5 人工审批流程

#### 2.5.1 审批检测

```java
public class ToolNode implements AsyncNodeAction {
    
    private final Set<String> WRITE_OPERATIONS = Set.of(
        "publishJob", "deleteJob", "createJob", "updateJobConfig"
    );
    
    @Override
    public CompletableFuture<NodeOutput> apply(OverAllState state, RunnableConfig config) {
        List<ToolCall> toolCalls = state.value("toolCalls").orElse(List.of());
        
        for (ToolCall toolCall : toolCalls) {
            if (requiresApproval(toolCall)) {
                // 标记需要审批
                state.update("approvalPending", true);
                state.update("pendingToolCall", toolCall);
                
                // 返回 INTERRUPT 类型的 NodeOutput
                return CompletableFuture.completedFuture(
                    NodeOutput.interrupt("APPROVAL_REQUIRED", Map.of(
                        "toolName", toolCall.name(),
                        "arguments", toolCall.arguments(),
                        "description", getApprovalDescription(toolCall)
                    ))
                );
            }
        }
        
        // 执行工具...
    }
    
    private boolean requiresApproval(ToolCall toolCall) {
        return WRITE_OPERATIONS.contains(toolCall.name());
    }
}
```

#### 2.5.2 Checkpoint 保存

```java
@Configuration
public class CheckpointConfig {
    
    @Bean
    public CompiledGraph agentGraphWithCheckpoint(
            StateGraph agentGraph,
            RedisTemplate<String, Object> redisTemplate
    ) {
        SaverConfig saverConfig = SaverConfig.builder()
            .register(SaverEnum.REDIS.getValue(), new RedisSaver(redisTemplate))
            .build();
        
        CompileConfig compileConfig = CompileConfig.builder()
            .saverConfig(saverConfig)
            .checkpointSaver(SaverEnum.REDIS.getValue())
            .build();
        
        return agentGraph.compile(compileConfig);
    }
}
```

#### 2.5.3 前端审批卡片

```vue
<!-- 审批卡片组件 -->
<div v-if="msg.approval" class="approval-card">
  <el-card>
    <template #header>
      <div class="approval-header">
        <el-icon><Warning /></el-icon>
        <span>需要确认操作</span>
      </div>
    </template>
    
    <div class="approval-content">
      <p><strong>操作：</strong>{{ msg.approval.toolName }}</p>
      <p><strong>说明：</strong>{{ msg.approval.description }}</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item 
          v-for="(value, key) in msg.approval.arguments" 
          :key="key"
          :label="key"
        >
          {{ value }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
    
    <template #footer>
      <div class="approval-actions">
        <el-button type="primary" @click="handleApprove(msg)">
          确认执行
        </el-button>
        <el-button @click="handleReject(msg)">
          取消
        </el-button>
      </div>
    </template>
  </el-card>
</div>
```

#### 2.5.4 Resume 接口

```java
@RestController
@RequestMapping("/api/ai")
public class ApprovalController {
    
    @PostMapping("/conversations/{conversationId}/approve")
    public Flux<ServerSentEvent<Map<String, Object>>> approveAndResume(
            @PathVariable Long conversationId,
            @RequestBody ApprovalRequest request
    ) {
        String checkpointId = request.getCheckpointId();
        String decision = request.getDecision(); // "APPROVE" or "REJECT"
        
        // 从 Redis 恢复状态
        RunnableConfig config = RunnableConfig.builder()
            .threadId(checkpointId)
            .build();
        
        // 更新状态
        Map<String, Object> resumeState = new HashMap<>();
        resumeState.put("approvalDecision", decision);
        resumeState.put("approvalPending", false);
        
        if ("REJECT".equals(decision)) {
            // 拒绝：清空待执行工具
            resumeState.put("toolCalls", List.of());
        }
        
        // Resume 图执行
        Flux<NodeOutput> outputStream = compiledGraph.stream(resumeState, config);
        
        // 转换为 SSE
        return convertToSSE(outputStream);
    }
}
```

### 2.6 流式输出适配

#### 2.6.1 NodeOutput 类型处理

```java
private Flux<ServerSentEvent<Map<String, Object>>> convertToSSE(Flux<NodeOutput> outputStream) {
    return outputStream.flatMap(output -> {
        String nodeType = output.getNodeType();
        
        switch (nodeType) {
            case "agent":
                // Agent 节点：提取文本 Token
                return extractTokens(output);
                
            case "tools":
                // Tool 节点：发送工具调用事件
                return emitToolEvents(output);
                
            case "INTERRUPT":
                // 中断：发送审批请求
                return emitApprovalRequest(output);
                
            default:
                return Flux.empty();
        }
    });
}

private Flux<ServerSentEvent<Map<String, Object>>> extractTokens(NodeOutput output) {
    // 从 output.getState() 获取 messages
    // 提取最新的 assistant message
    // 逐字符流式推送
    String content = extractLatestContent(output.getState());
    
    return Flux.fromIterable(content.chars().boxed())
        .map(c -> ServerSentEvent.<Map<String, Object>>builder()
            .event("text")
            .data(Map.of("content", String.valueOf((char) c.intValue())))
            .build());
}
```

#### 2.6.2 保留自定义事件

```java
// 在 ToolNode 中发射自定义事件
private void emitTableEvent(OverAllState state, String eventType, Object payload) {
    // 将事件存入状态
    List<CustomEvent> events = state.value("customEvents").orElse(new ArrayList<>());
    events.add(new CustomEvent(eventType, payload));
    state.update("customEvents", events);
}

// 在 convertToSSE 中提取并发送
private Flux<ServerSentEvent<Map<String, Object>>> emitToolEvents(NodeOutput output) {
    List<CustomEvent> events = output.getState().value("customEvents").orElse(List.of());
    
    return Flux.fromIterable(events)
        .map(event -> ServerSentEvent.<Map<String, Object>>builder()
            .event(event.getType())
            .data(event.getPayload())
            .build());
}
```

### 2.7 审计表设计

```sql
CREATE TABLE `tool_audit_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tool_call_id` VARCHAR(64) NOT NULL COMMENT '工具调用ID',
  `conversation_id` BIGINT COMMENT '会话ID',
  `user_id` VARCHAR(64) COMMENT '用户ID',
  `tool_name` VARCHAR(128) NOT NULL COMMENT '工具名称',
  `tool_source` VARCHAR(64) COMMENT '工具来源：INTERNAL, MCP_LOGISTICS, MCP_AFTERSALE',
  `arguments` TEXT COMMENT '调用参数（JSON）',
  `result_summary` TEXT COMMENT '结果摘要',
  `status` VARCHAR(32) NOT NULL COMMENT '状态：SUCCESS, FAILED, TIMEOUT, VALIDATION_FAILED, PERMISSION_DENIED',
  `error_message` TEXT COMMENT '错误信息',
  `retry_count` INT DEFAULT 0 COMMENT '重试次数',
  `duration` BIGINT COMMENT '执行耗时（毫秒）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_tool_name` (`tool_name`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用审计日志表';
```

---

## 三、任务分解与实施顺序

### 阶段1：基础设施搭建（2-3天）

**任务1.1：添加依赖**
- 添加 JSON Schema Validator 依赖
- 添加 Spring AI MCP Client/Server Starter
- 添加 Resilience4j（如需要）

**任务1.2：数据库准备**
- 创建 tool_audit_logs 表
- 创建对应的 Entity 和 Mapper

**任务1.3：配置 Redis Checkpoint**
- 配置 RedisSaver
- 配置 CompileConfig

### 阶段2：StateGraph 核心实现（3-4天）

**任务2.1：状态定义**
- 创建 AgentState 类
- 实现 OverAllStateFactory

**任务2.2：Agent 节点**
- 实现 AgentNode
- 集成 ChatClient
- 解析工具调用决策

**任务2.3：Tool 节点**
- 实现 ToolNode
- 集成装饰器链
- 处理审批逻辑

**任务2.4：图构建**
- 组装 StateGraph
- 配置条件边
- 编译为 CompiledGraph

### 阶段3：装饰器链实现（4-5天）

**任务3.1：审计装饰器**
- 实现 AuditToolCallback
- 实现 ToolAuditService（异步保存）

**任务3.2：参数校验装饰器**
- 集成 networknt/json-schema-validator
- 实现 ValidationToolCallback

**任务3.3：权限校验装饰器**
- 实现 ToolPermissionConfig
- 实现 PermissionToolCallback

**任务3.4：超时装饰器**
- 实现 TimeoutToolCallback
- 配置工具超时时间

**任务3.5：重试装饰器**
- 实现 RetryToolCallback
- 配置重试策略（区分读写）

**任务3.6：结果格式化装饰器**
- 实现 FormattingToolCallback
- 配置字段裁剪和枚举翻译

**任务3.7：装饰器链组装**
- 实现 ToolGovernanceConfig
- 自动包装所有工具



### 阶段5：人工审批流程（2-3天）

**任务5.1：审批检测**
- 在 ToolNode 中实现审批判断
- 触发 interrupt

**任务5.2：前端审批卡片**
- 设计审批卡片 UI
- 处理 INTERRUPT 事件
- 显示审批信息

**任务5.3：Resume 接口**
- 实现 /api/ai/conversations/{id}/approve
- 从 checkpoint 恢复
- 更新状态并 resume

### 阶段6：流式输出适配（2-3天）

**任务6.1：NodeOutput 处理**
- 实现 convertToSSE 方法
- 处理不同节点类型

**任务6.2：Token 提取**
- 从 Agent 节点提取文本增量
- 保持逐字渲染

**任务6.3：自定义事件保留**
- 保留表格事件
- 保留工具调用事件
- 确保前端兼容

### 阶段7：测试与验收（2-3天）

**任务7.1：单元测试**
- 装饰器链测试
- 审计逻辑测试
- 权限校验测试

**任务7.2：集成测试**
- StateGraph 完整流程测试
- MCP 工具调用测试
- 人工审批流程测试

**任务7.3：验收标准测试**
- 验收1：新增工具自动发现
- 验收2：发布作业审批流程
- 验收3：审批拒绝 + 审计记录
- 验收4：超时重试机制

---

## 四、风险与挑战

### 4.1 技术风险

**风险1：StateGraph 流式输出的 Token 提取**
- 问题：NodeOutput 可能不直接包含 Token 增量
- 缓解：在 Agent 节点内部使用 ChatClient.stream()，将 Token 存入状态
- 备选：使用自定义事件机制模拟 Token 流

**风险2：Checkpoint Resume 的状态一致性**
- 问题：Resume 后状态可能不完整
- 缓解：确保所有关键状态都持久化到 Redis
- 备选：在 Resume 前验证状态完整性


### 4.2 性能风险

**风险4：装饰器链开销**
- 问题：多层装饰器可能影响性能
- 缓解：异步化审计写入，超时和重试用独立线程池
- 监控：记录每层装饰器的耗时

**风险5：Redis Checkpoint 的性能**
- 问题：频繁保存 checkpoint 可能影响吞吐
- 缓解：只在需要审批时保存 checkpoint
- 备选：使用内存 checkpoint（不持久化）

### 4.3 兼容性风险

**风险6：前端 SSE 事件格式变更**
- 问题：从 Flux<String> 改为 Flux<NodeOutput> 后，前端事件格式可能不兼容
- 缓解：在 convertToSSE 中保持现有事件格式（text, table_start, tool_start）
- 测试：回归测试前端表格渲染和工具调用显示

---

## 五、依赖与配置

### 5.1 Maven 依赖

```xml
<!-- JSON Schema Validator -->
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.0.87</version>
</dependency>

<!-- Spring AI MCP Client -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- Spring AI Alibaba Graph (已包含在 spring-ai-alibaba-starter-dashscope 中) -->
```

### 5.2 配置文件

**application.yml**：
```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: data-platform-client
        type: ASYNC
        request-timeout: 30s
        streamable-http:
          connections:
            logistics:
              url: http://localhost:8091/mcp
            aftersale:
              url: http://localhost:8092/mcp
  
  redis:
    host: localhost
    port: 6379
    database: 0

# 工具治理配置
tool:
  governance:
    timeout:
      default: 10s
      overrides:
        publishJob: 30s
        deleteJob: 30s
    retry:
      max-retries: 2
      delay: 1s
      write-operations:
        - publishJob
        - deleteJob
        - createJob
        - updateJobConfig
    permission:
      rules:
        - tool: publishJob
          roles: [ADMIN, OPERATOR]
        - tool: deleteJob
          roles: [ADMIN]
    formatting:
      field-filters:
        getJobDetails: [id, jobName, jobCnName, status, sourceTable, targetTable]
```

---

## 六、验收检查清单

### 验收1：新增工具自动发现
- [ ] 在 JobTool 中新增一个 `@Tool` 方法
- [ ] 不修改 StateGraph 或 Controller
- [ ] 启动应用，与 AI 对话
- [ ] AI 能识别并调用新工具
- [ ] 工具调用被审计日志记录

### 验收2：发布作业审批流程
- [ ] 聊天输入："发布作业"
- [ ] AI 追问作业 ID 和版本
- [ ] 补充信息后，前端显示审批卡片
- [ ] 卡片包含：工具名、参数、说明
- [ ] 点击"确认执行"，作业发布成功
- [ ] 审计日志记录为 SUCCESS

### 验收3：审批拒绝 + 审计记录
- [ ] 重复验收2的步骤，到审批卡片
- [ ] 点击"取消"
- [ ] 作业未发布
- [ ] 审计日志记录为 PERMISSION_DENIED（或新增 REJECTED 状态）
- [ ] AI 回复"操作已取消"

### 验收4：超时重试机制
- [ ] 人为让一个读工具（如 getJobDetails）超时
  - 方法：在工具内 `Thread.sleep(15000)`
- [ ] 观察审计日志：
  - status: TIMEOUT
  - retry_count: 2（默认重试2次）
  - duration: >30000ms
- [ ] 人为让一个写工具（如 publishJob）超时
- [ ] 观察审计日志：
  - status: TIMEOUT
  - retry_count: 0（写操作不重试）
  - duration: >30000ms

---

## 七、交付物

1. **代码**：
   - StateGraph 实现（Agent节点、Tool节点）
   - 装饰器链（6个装饰器 + 组装配置）
   - MCP Server（物流、售后，2个独立项目）
   - MCP Client 配置与集成
   - 审批流程（interrupt + resume）
   - 流式输出适配
   - 审计服务

2. **数据库**：
   - tool_audit_logs 表及迁移脚本

3. **配置**：
   - application.yml 更新
   - 工具治理配置

4. **测试**：
   - 单元测试（装饰器、审计）
   - 集成测试（StateGraph、MCP）
   - 验收测试脚本

5. **文档**：
   - dev-notes/ch08.md（开发日志）
   - dev-notes/ch08-plan.md（本计划）
   - README 更新（新功能说明）

---

## 八、时间估算

- 阶段1：基础设施搭建 - 2天
- 阶段2：StateGraph 核心实现 - 3天
- 阶段3：装饰器链实现 - 4天
- 阶段5：人工审批流程 - 2天
- 阶段6：流式输出适配 - 2天
- 阶段7：测试与验收 - 2天

**总计：18天（约3.5周）**

---

## 九、后续优化方向

1. **工具注册中心 UI**：可视化查看所有工具（内置 + MCP）
2. **审批历史查询**：前端展示历史审批记录
3. **工具性能监控**：Grafana 仪表盘展示工具调用性能
4. **动态权限配置**：通过 Admin UI 修改权限规则，无需重启
5. **工具版本管理**：支持工具多版本并存
