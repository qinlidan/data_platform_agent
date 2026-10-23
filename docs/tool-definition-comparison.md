# 生产级别 Tool 定义：@Tool 注解 vs JSON Schema

## 一、当前实现（@Tool 注解）

```java
@Tool(description = """
    查询作业详情。根据作业编码（jobCode）获取作业的完整信息...
    """)
public String getJobDetails(
    @ToolParam(description = "作业编码，例如 job_001") String jobCode
) {
    // 实现
}
```

**底层原理：**
Spring AI 在运行时通过反射扫描 @Tool 注解，自动生成如下 JSON Schema：

```json
{
  "type": "function",
  "function": {
    "name": "getJobDetails",
    "description": "查询作业详情。根据作业编码...",
    "parameters": {
      "type": "object",
      "properties": {
        "jobCode": {
          "type": "string",
          "description": "作业编码，例如 job_001"
        }
      },
      "required": ["jobCode"]
    }
  }
}
```

---

## 二、手动 JSON Schema 定义

```java
@Configuration
public class ToolConfig {
    
    @Bean
    public FunctionCallback getJobDetailsFunction(JobService jobService) {
        return FunctionCallback.builder()
            .function("getJobDetails", (String jobCode) -> {
                // 实现
            })
            .description("查询作业详情...")
            .inputType(String.class)
            // 手动定义 Schema
            .schema("""
                {
                  "type": "object",
                  "properties": {
                    "jobCode": {
                      "type": "string",
                      "description": "作业编码",
                      "pattern": "^job_[a-zA-Z0-9_]+$",
                      "minLength": 5,
                      "maxLength": 50,
                      "examples": ["job_user_001", "job_order_sync"]
                    }
                  },
                  "required": ["jobCode"],
                  "additionalProperties": false
                }
            """)
            .build();
    }
}
```

---

## 三、对比分析

### @Tool 注解（推荐用于）

✅ **优点：**
1. **简洁易维护**：代码即文档，参数类型、描述一目了然
2. **类型安全**：编译期检查，不会写错参数名
3. **自动序列化/反序列化**：Spring AI 自动处理 JSON ↔ Java 对象
4. **IDE 友好**：重构工具名、参数名时自动同步
5. **适合快速迭代**：添加新工具只需写一个方法

❌ **局限：**
1. **Schema 控制有限**：无法定义 `pattern`、`minLength`、`enum`、`examples` 等高级约束
2. **描述分散**：方法注释和 @Tool description 容易不一致
3. **复杂类型支持差**：嵌套对象、泛型、Optional 等处理不够精细
4. **无法做参数依赖**：比如 "如果 type=create，则 tableName 必填"

**适用场景：**
- ✅ CRUD 类工具（你的项目就是典型）
- ✅ 参数简单（基本类型 + 简单 POJO）
- ✅ 团队熟悉 Spring 生态
- ✅ 快速开发、快速迭代

---

### 手动 JSON Schema（推荐用于）

✅ **优点：**
1. **完全控制**：可以定义任何 JSON Schema 高级特性
2. **精确验证**：pattern、range、enum、conditional schema
3. **更好的提示**：examples、default 值让 AI 更准确
4. **跨语言一致**：同一份 Schema 可用于前端、文档、测试
5. **版本化管理**：Schema 可以独立版本控制

❌ **缺点：**
1. **维护成本高**：手写 JSON 容易出错，字符串拼接易错
2. **代码冗长**：100 行注解代码可能变成 300 行 JSON
3. **类型不安全**：重构方法参数时，JSON 不会自动更新
4. **调试困难**：Schema 错误在运行时才能发现

**适用场景：**
- ✅ **金融/医疗等强监管行业**：需要严格的参数校验和审计
- ✅ **复杂业务规则**：参数之间有依赖关系（if/then/else）
- ✅ **多语言团队**：Schema 可以共享给 Python/Node.js
- ✅ **API 即产品**：工具定义本身需要对外发布（如 OpenAI Plugin）

---

## 四、生产级别的混合方案（推荐）

```java
@Component
@Slf4j
public class JobTool {
    
    // 简单工具：用 @Tool
    @Tool(description = "查询作业详情")
    public String getJobDetails(
        @ToolParam(description = "作业编码") String jobCode
    ) {
        // 实现
    }
    
    // 复杂工具：手动 Schema + 注解结合
    @Tool(
        description = "创建作业",
        // 引用外部 Schema 文件
        schemaResource = "classpath:schemas/create-job-schema.json"
    )
    public String createJob(JobCreateRequest request) {
        // 实现
    }
}
```

**外部 Schema 文件（`resources/schemas/create-job-schema.json`）：**

```json
{
  "type": "object",
  "properties": {
    "jobCode": {
      "type": "string",
      "pattern": "^job_[a-z0-9_]{3,40}$",
      "description": "作业编码，必须以 job_ 开头",
      "examples": ["job_user_sync", "job_order_etl"]
    },
    "jobType": {
      "type": "string",
      "enum": ["FULL", "INCREMENT", "REALTIME"],
      "description": "作业类型"
    },
    "engineType": {
      "type": "string",
      "enum": ["FLINK", "SPARK", "DATAX"],
      "description": "执行引擎"
    },
    "sourceTable": {
      "type": "string",
      "pattern": "^[a-zA-Z_][a-zA-Z0-9_]*$",
      "minLength": 1,
      "maxLength": 64,
      "description": "源表名（合法的 SQL 标识符）"
    }
  },
  "required": ["jobCode", "jobType", "engineType"],
  "allOf": [
    {
      "if": {
        "properties": { "jobType": { "const": "FULL" } }
      },
      "then": {
        "required": ["sourceTable", "targetTable"]
      }
    }
  ]
}
```

---

## 五、企业级最佳实践

### 1. 阿里云/腾讯云等大厂做法

```java
// 1. 工具定义分层
@ToolGroup(name = "job-management", version = "1.0")
public class JobTools {
    
    // 2. 每个工具都有独立的 Request DTO
    @Tool(description = "创建作业")
    public ToolResult<JobDTO> createJob(
        @Valid JobCreateRequest request  // ✅ 使用 JSR-303 验证
    ) {
        // 3. 返回结构化对象，不是纯字符串
        return ToolResult.success(jobDTO);
    }
}

// 4. Request DTO 自带验证规则
@Data
@Schema(description = "创建作业请求")
public class JobCreateRequest {
    
    @NotBlank(message = "作业编码不能为空")
    @Pattern(regexp = "^job_[a-z0-9_]{3,40}$", message = "作业编码格式不正确")
    @Schema(description = "作业编码", example = "job_user_001")
    private String jobCode;
    
    @NotNull
    @Schema(description = "作业类型")
    private JobType jobType;  // Enum 自动转为 JSON Schema enum
    
    // 5. 使用 OpenAPI 注解（Swagger）自动生成 Schema
}
```

### 2. OpenAI Plugin 标准（公开 API）

```yaml
# openapi.yaml
paths:
  /api/tools/job/create:
    post:
      operationId: createJob
      summary: 创建 ETL 作业
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/JobCreateRequest'
              
components:
  schemas:
    JobCreateRequest:
      type: object
      properties:
        jobCode:
          type: string
          pattern: '^job_[a-z0-9_]{3,40}$'
          examples:
            - job_user_sync
      required:
        - jobCode
```

**然后用代码自动加载：**
```java
@Configuration
public class ToolConfig {
    
    @Bean
    public List<FunctionCallback> toolsFromOpenAPI() {
        return OpenAPIParser.parse("classpath:openapi.yaml")
            .toFunctionCallbacks();
    }
}
```

---

## 六、你的项目建议

### 当前状态（84 个 Java 文件，工具较简单）

✅ **继续使用 @Tool 注解**

**理由：**
1. 你的工具都是标准的 CRUD，参数简单（String、enum、基本类型）
2. 团队是 Java/Spring 技术栈，@Tool 更自然
3. 没有复杂的参数依赖关系
4. 不需要对外发布 API

### 建议改进点

```java
// ❌ 当前：返回纯字符串
@Tool(description = "查询作业详情")
public String getJobDetails(String jobCode) {
    return String.format("作业详情：...");
}

// ✅ 改进：返回结构化对象
@Tool(description = "查询作业详情")
public ToolResult<JobDTO> getJobDetails(
    @ToolParam(description = "作业编码") String jobCode
) {
    Job job = jobService.getByCode(jobCode);
    if (job == null) {
        return ToolResult.error("作业不存在");
    }
    return ToolResult.success(JobDTO.from(job));
}

// ToolResult 包装类
@Data
public class ToolResult<T> {
    private boolean success;
    private String message;
    private T data;
    
    public static <T> ToolResult<T> success(T data) {
        return new ToolResult<>(true, null, data);
    }
    
    public static <T> ToolResult<T> error(String message) {
        return new ToolResult<>(false, message, null);
    }
}
```

**优势：**
- AI 可以更容易解析结构化数据
- 前端可以直接拿到 JSON，不用解析自然语言
- 便于后续扩展（加字段不影响现有逻辑）

---

## 七、什么时候必须用 JSON Schema？

### 场景 1：金融/医疗/政务（强合规）

```java
// 银行转账工具：金额必须精确到分，范围限制
{
  "properties": {
    "amount": {
      "type": "number",
      "multipleOf": 0.01,
      "minimum": 0.01,
      "maximum": 50000.00,
      "description": "转账金额（元），精确到分"
    },
    "accountNumber": {
      "type": "string",
      "pattern": "^[0-9]{16,19}$",
      "description": "银行账号（16-19 位数字）"
    }
  }
}
```

### 场景 2：复杂参数依赖

```json
{
  "if": {
    "properties": { "jobType": { "const": "REALTIME" } }
  },
  "then": {
    "properties": {
      "kafkaTopic": {
        "type": "string",
        "description": "实时作业必须指定 Kafka Topic"
      }
    },
    "required": ["kafkaTopic"]
  }
}
```

### 场景 3：多租户/多版本

```java
// 不同客户需要不同的 Schema
Map<String, JsonSchema> schemas = Map.of(
    "enterprise", enterpriseSchema,  // 企业版：功能全开
    "standard", standardSchema,      // 标准版：部分功能
    "trial", trialSchema             // 试用版：限制严格
);

FunctionCallback tool = FunctionCallback.builder()
    .schema(schemas.get(currentTenant.getEdition()))
    .build();
```

---

## 总结

| 项目特征 | 推荐方案 |
|---------|---------|
| **你的项目（CRUD 工具、团队协作）** | **@Tool 注解** ✅ |
| 金融/医疗（严格验证） | JSON Schema |
| API 对外开放（OpenAI Plugin） | OpenAPI YAML |
| 复杂参数依赖（if/then） | JSON Schema |
| 快速迭代（创业公司） | @Tool 注解 |
| 多语言团队 | JSON Schema |

**最佳实践：**
- 80% 的工具用 @Tool 注解（简洁高效）
- 20% 的关键工具用 JSON Schema（精确控制）
- 使用 JSR-303 验证增强注解能力
- 返回结构化对象，不是纯字符串
