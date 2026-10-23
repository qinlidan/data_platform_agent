# 生产级别 AI Tool 定义：校验 + 高危操作

## 问题分析

### 当前架构的问题

```java
// ❌ 问题 1：参数是原始类型，无法使用 JSR-303 校验
@Tool(description = "创建作业")
public String createJob(
    @ToolParam(description = "jobCode") String jobCode,  // 无法添加 @Pattern
    @ToolParam(description = "jobType") String jobType,  // 无法添加 @NotNull
    // ... 8 个参数
) {
    // ❌ 问题 2：手动校验，AI 无法提前知道规则
    if (jobCode == null || !jobCode.matches("^job_.*")) {
        return "作业编码格式错误";
    }
    
    // ❌ 问题 3：字符串转枚举可能失败
    request.setJobType(JobType.valueOf(jobType)); // 运行时异常
}

// ❌ 问题 4：高危操作的二次确认不优雅
@Tool(description = "删除作业")
public String deleteJob(
    @ToolParam(description = "jobCode") String jobCode,
    @ToolParam(description = "confirmed") Boolean confirmed  // AI 需要调用两次
) {
    if (!confirmed) {
        return "⚠️ 确认删除吗？";
    }
    // 删除逻辑
}
```

---

## 解决方案 1：混合使用 @Tool + JSON Schema（推荐）

### 1.1 简单工具保持 @Tool

```java
@Tool(description = "查询作业详情")
public String getJobDetails(
    @ToolParam(description = "作业编码") String jobCode
) {
    // 简单查询，不需要复杂校验
}
```

### 1.2 复杂工具：接收 DTO 对象 + 自定义 Schema

```java
@Tool(
    description = "创建 ETL 作业",
    // 引用自定义 Schema
    inputSchema = """
        {
          "type": "object",
          "properties": {
            "jobCode": {
              "type": "string",
              "pattern": "^job_[a-z0-9_]{3,40}$",
              "description": "作业编码，格式：job_xxx",
              "examples": ["job_user_sync", "job_order_001"]
            },
            "jobName": {
              "type": "string",
              "minLength": 1,
              "maxLength": 100,
              "description": "作业名称"
            },
            "jobType": {
              "type": "string",
              "enum": ["FULL", "INCREMENT", "REALTIME"],
              "description": "作业类型：FULL=全量, INCREMENT=增量, REALTIME=实时"
            },
            "engineType": {
              "type": "string",
              "enum": ["FLINK", "SPARK", "DATAX"],
              "description": "执行引擎"
            },
            "sourceTable": {
              "type": "string",
              "pattern": "^[a-zA-Z_][a-zA-Z0-9_]*$",
              "maxLength": 64,
              "description": "源表名（合法的 SQL 标识符）"
            }
          },
          "required": ["jobCode", "jobName", "jobType", "engineType"],
          "additionalProperties": false
        }
    """
)
public ToolResult<JobDTO> createJob(JobCreateRequest request) {
    // Spring AI 会自动：
    // 1. 根据 Schema 校验 AI 生成的参数
    // 2. 反序列化为 JobCreateRequest
    // 3. 触发 JSR-303 校验（@NotBlank、@Pattern 等）
    
    try {
        Job job = jobService.create(request);
        return ToolResult.success(JobDTO.from(job));
    } catch (BusinessException e) {
        return ToolResult.error(e.getMessage());
    }
}
```

---

## 解决方案 2：使用 JSR-303 + 自动生成 Schema（最优雅）

### 2.1 增强 DTO 定义

```java
@Data
@Schema(description = "创建作业请求")
public class JobCreateRequest {
    
    @NotBlank(message = "作业编码不能为空")
    @Pattern(regexp = "^job_[a-z0-9_]{3,40}$", message = "作业编码格式：job_xxx（3-40 字符）")
    @Schema(
        description = "作业编码，格式：job_xxx",
        example = "job_user_sync",
        pattern = "^job_[a-z0-9_]{3,40}$"
    )
    private String jobCode;
    
    @NotBlank(message = "作业名称不能为空")
    @Size(min = 1, max = 100, message = "作业名称长度为 1-100")
    @Schema(description = "作业名称", example = "用户数据同步作业")
    private String jobName;
    
    @NotNull(message = "作业类型不能为空")
    @Schema(
        description = "作业类型",
        allowableValues = {"FULL", "INCREMENT", "REALTIME"},
        example = "FULL"
    )
    private JobType jobType;
    
    @NotNull(message = "执行引擎不能为空")
    @Schema(
        description = "执行引擎",
        allowableValues = {"FLINK", "SPARK", "DATAX"}
    )
    private EngineType engineType;
    
    @NotBlank(message = "源表名不能为空")
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "源表名必须是合法的 SQL 标识符")
    @Size(max = 64, message = "源表名最长 64 字符")
    @Schema(
        description = "源表名（合法的 SQL 标识符）",
        example = "user_info",
        pattern = "^[a-zA-Z_][a-zA-Z0-9_]*$"
    )
    private String sourceTable;
    
    // 条件校验：实时作业必须指定 Kafka Topic
    @Schema(description = "Kafka Topic（实时作业必填）")
    private String kafkaTopic;
    
    @AssertTrue(message = "实时作业必须指定 Kafka Topic")
    private boolean isKafkaTopicValid() {
        if (jobType == JobType.REALTIME) {
            return kafkaTopic != null && !kafkaTopic.isEmpty();
        }
        return true;
    }
}
```

### 2.2 配置 Schema 自动生成

```java
@Configuration
public class ToolSchemaConfig {
    
    @Bean
    public SchemaGenerator schemaGenerator() {
        return new JsonSchemaGenerator();
    }
}

// 自定义注解：标记需要生成 Schema 的 Tool
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolWithSchema {
    String description();
    Class<?> requestType();  // 自动从 DTO 生成 Schema
}
```

### 2.3 简化的 Tool 定义

```java
@Component
@Slf4j
public class JobTool {
    
    // ✅ 简洁：不用手写 Schema，从 DTO 自动生成
    @ToolWithSchema(
        description = "创建 ETL 作业",
        requestType = JobCreateRequest.class  // 自动生成 JSON Schema
    )
    public ToolResult<JobDTO> createJob(@Valid JobCreateRequest request) {
        try {
            Job job = jobService.create(request);
            return ToolResult.success(JobDTO.from(job));
        } catch (BusinessException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
```

---

## 解决方案 3：高危操作的优雅处理

### 3.1 问题：当前的二次确认不优雅

```java
// ❌ 问题：AI 需要记住状态，调用两次
@Tool(description = "删除作业")
public String deleteJob(
    @ToolParam(description = "jobCode") String jobCode,
    @ToolParam(description = "confirmed") Boolean confirmed
) {
    if (!confirmed) {
        return "⚠️ 确认删除吗？";  // AI 需要再次调用并传 confirmed=true
    }
    jobService.delete(jobCode);
}
```

### 3.2 方案 A：人机交互模式（推荐）

```java
@Tool(description = """
    删除作业（高危操作）。
    
    ⚠️ 此操作不可恢复！
    调用后会返回确认令牌（token），请将 token 明确告知用户，
    等待用户确认后，使用 confirmDeleteJob 工具执行删除。
    """)
public ToolResult<DeleteConfirmation> requestDeleteJob(
    @ToolParam(description = "作业编码") String jobCode
) {
    Job job = jobService.getByCode(jobCode);
    if (job == null) {
        return ToolResult.error("作业不存在");
    }
    
    // 生成一次性确认令牌（5 分钟有效）
    String token = confirmationService.generateToken(
        "DELETE_JOB",
        jobCode,
        Duration.ofMinutes(5)
    );
    
    DeleteConfirmation confirmation = new DeleteConfirmation();
    confirmation.setJobCode(jobCode);
    confirmation.setJobName(job.getJobName());
    confirmation.setToken(token);
    confirmation.setMessage(String.format(
        "⚠️ 将删除作业 %s（%s），此操作不可恢复！\n" +
        "确认令牌：%s\n" +
        "请向用户确认后，使用 confirmDeleteJob 工具执行删除。",
        jobCode, job.getJobName(), token
    ));
    
    return ToolResult.success(confirmation);
}

@Tool(description = """
    确认删除作业。必须先调用 requestDeleteJob 获取确认令牌。
    """)
public ToolResult<Void> confirmDeleteJob(
    @ToolParam(description = "确认令牌") String token
) {
    // 验证令牌
    ConfirmationData data = confirmationService.validateToken(token);
    if (data == null) {
        return ToolResult.error("确认令牌无效或已过期");
    }
    
    String jobCode = data.getTargetId();
    jobService.delete(jobCode);
    
    // 令牌一次性使用后销毁
    confirmationService.revokeToken(token);
    
    return ToolResult.success(null, "作业已删除");
}
```

**流程：**
1. 用户："删除 job_001"
2. AI 调用 `requestDeleteJob("job_001")`，得到 token
3. AI 回复用户："⚠️ 将删除作业 job_001，确认吗？（令牌：abc123）"
4. 用户："确认"
5. AI 调用 `confirmDeleteJob("abc123")`，执行删除

### 3.3 方案 B：审批流模式（企业级）

```java
@Tool(description = "删除作业（需审批）")
public ToolResult<ApprovalRequest> deleteJob(
    @ToolParam(description = "作业编码") String jobCode,
    @ToolParam(description = "删除原因") String reason
) {
    // 创建审批单
    ApprovalRequest approval = approvalService.createApproval(
        ApprovalType.DELETE_JOB,
        jobCode,
        reason,
        getCurrentUser()
    );
    
    // 发送钉钉/企微通知给审批人
    notificationService.notifyApprovers(approval);
    
    return ToolResult.success(approval, 
        String.format("已提交删除申请（审批单号：%s），等待审批", approval.getId())
    );
}

// 审批通过后，由审批系统调用实际的删除接口
// AI 工具不直接执行删除
```

### 3.4 方案 C：干运行模式（Dry Run）

```java
@Tool(description = "发布作业（高危操作）")
public ToolResult<PublishPreview> publishJob(
    @ToolParam(description = "作业编码") String jobCode,
    @ToolParam(description = "版本号") Integer version,
    @ToolParam(description = "是否真正执行，默认为 false（预演模式）") 
    Boolean execute
) {
    Job job = jobService.getByCode(jobCode);
    
    // 默认 dry-run：只返回影响分析，不实际执行
    if (execute == null || !execute) {
        PublishPreview preview = publishService.preview(job, version);
        preview.setWarning("⚠️ 这是预演模式，未实际发布。确认无误后，请回复\"确认发布\"");
        return ToolResult.success(preview);
    }
    
    // 真正执行
    publishService.publish(job.getId(), version);
    return ToolResult.success(null, "发布成功");
}
```

**预演结果示例：**
```json
{
  "success": true,
  "data": {
    "jobCode": "job_001",
    "currentVersion": 3,
    "targetVersion": 4,
    "affectedTables": ["ods.user_info", "dw.dim_user"],
    "estimatedDowntime": "30 秒",
    "dependencies": ["job_002 依赖此作业"],
    "warning": "⚠️ 这是预演模式，未实际发布。确认无误后，请回复\"确认发布\""
  }
}
```

---

## 解决方案 4：分层工具定义（大型项目）

```java
// 1. 基础层：只读工具（无需特殊校验）
@Component
public class JobQueryTools {
    @Tool(description = "查询作业详情")
    public String getJobDetails(String jobCode) { }
    
    @Tool(description = "搜索作业")
    public String searchJobs(String keyword) { }
}

// 2. 操作层：写操作工具（需要校验）
@Component
public class JobOperationTools {
    @ToolWithSchema(
        description = "创建作业",
        requestType = JobCreateRequest.class,
        riskLevel = RiskLevel.MEDIUM  // 标记风险等级
    )
    public ToolResult<JobDTO> createJob(@Valid JobCreateRequest request) { }
    
    @ToolWithSchema(
        description = "修改作业",
        requestType = JobUpdateRequest.class,
        riskLevel = RiskLevel.MEDIUM
    )
    public ToolResult<JobDTO> updateJob(@Valid JobUpdateRequest request) { }
}

// 3. 高危层：需要二次确认的工具
@Component
public class JobDangerousTools {
    @ToolWithConfirmation(
        description = "删除作业",
        riskLevel = RiskLevel.HIGH,
        confirmationMessage = "⚠️ 此操作不可恢复！"
    )
    public ToolResult<Void> deleteJob(String jobCode) { }
    
    @ToolWithConfirmation(
        description = "发布作业到生产环境",
        riskLevel = RiskLevel.CRITICAL,
        confirmationMessage = "⚠️ 将影响生产环境！",
        requireApproval = true  // 需要审批
    )
    public ToolResult<Void> publishJob(String jobCode, Integer version) { }
}
```

### 配置工具加载策略

```java
@Configuration
public class AiToolConfig {
    
    @Bean
    public ToolRegistry toolRegistry(
        JobQueryTools queryTools,
        JobOperationTools operationTools,
        JobDangerousTools dangerousTools,
        UserContext userContext
    ) {
        ToolRegistry registry = new ToolRegistry();
        
        // 所有用户都能用查询工具
        registry.register(queryTools);
        
        // 开发者可以用操作工具
        if (userContext.hasRole("DEVELOPER")) {
            registry.register(operationTools);
        }
        
        // 只有管理员能用高危工具
        if (userContext.hasRole("ADMIN")) {
            registry.register(dangerousTools);
        }
        
        return registry;
    }
}
```

---

## 实际代码示例：你的项目改造

### 改造前（当前）

```java
@Tool(description = "创建作业")
public String createJob(
    @ToolParam(description = "jobCode") String jobCode,
    @ToolParam(description = "jobName") String jobName,
    // ... 8 个参数
) {
    // 手动校验
    if (jobCode == null || !jobCode.matches("^job_.*")) {
        return "作业编码格式错误";
    }
    
    // 手动转换
    request.setJobType(JobType.valueOf(jobType));  // 可能抛异常
}
```

### 改造后（推荐）

```java
// 1. 增强 DTO
@Data
@Schema(description = "创建作业请求")
public class JobCreateRequest {
    
    @NotBlank(message = "作业编码不能为空")
    @Pattern(regexp = "^job_[a-z0-9_]{3,40}$", message = "作业编码格式：job_xxx")
    @Schema(description = "作业编码", example = "job_user_001", pattern = "^job_[a-z0-9_]{3,40}$")
    private String jobCode;
    
    @NotBlank
    @Size(min = 1, max = 100)
    @Schema(description = "作业名称", example = "用户同步作业")
    private String jobName;
    
    @NotNull
    @Schema(description = "作业类型", allowableValues = {"FULL", "INCREMENT", "REALTIME"})
    private JobType jobType;
    
    // ... 其他字段
}

// 2. 简化 Tool 定义
@Tool(
    description = "创建 ETL 作业",
    schemaResource = "classpath:schemas/job-create-schema.json"  // 可选：引用外部 Schema
)
public ToolResult<JobDTO> createJob(@Valid JobCreateRequest request) {
    try {
        Job job = jobService.create(request);
        return ToolResult.success(JobDTO.from(job));
    } catch (BusinessException e) {
        return ToolResult.error(e.getMessage());
    }
}

// 3. 高危操作拆分
@Tool(description = "请求删除作业（返回确认令牌）")
public ToolResult<DeleteConfirmation> requestDeleteJob(
    @ToolParam(description = "作业编码") String jobCode
) {
    // 生成确认令牌
    String token = confirmationService.generateToken("DELETE_JOB", jobCode, Duration.ofMinutes(5));
    return ToolResult.success(new DeleteConfirmation(jobCode, token));
}

@Tool(description = "确认删除作业（需要确认令牌）")
public ToolResult<Void> confirmDeleteJob(
    @ToolParam(description = "确认令牌") String token
) {
    ConfirmationData data = confirmationService.validateToken(token);
    if (data == null) {
        return ToolResult.error("令牌无效或已过期");
    }
    jobService.delete(data.getTargetId());
    confirmationService.revokeToken(token);
    return ToolResult.success(null, "删除成功");
}
```

---

## 总结

| 需求 | 方案 | 实现方式 |
|------|------|----------|
| **参数校验（正则、长度、枚举）** | DTO + JSR-303 | `@Pattern`、`@Size`、枚举类型 |
| **复杂校验规则** | 自定义 JSON Schema | `pattern`、`minLength`、`enum` |
| **高危操作（删除/发布）** | 二次确认令牌 | `requestXxx` + `confirmXxx` 两个工具 |
| **企业级审批** | 审批流模式 | 创建审批单，钉钉/企微通知 |
| **预演模式** | Dry Run | `execute` 参数，默认 false |
| **权限控制** | 分层工具 + 角色 | 不同角色加载不同工具 |

**你的项目建议：**
1. ✅ 继续用 @Tool（不要完全改成手写 Schema）
2. ✅ 增强 DTO：添加 `@Pattern`、`@Size`、`@Schema` 注解
3. ✅ 高危操作改为二次确认令牌模式
4. ✅ 返回结构化 `ToolResult<T>`，不是纯字符串
