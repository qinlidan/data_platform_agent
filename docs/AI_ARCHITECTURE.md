# 🤖 数据平台 AI 增强架构设计

## 📋 目录
- [概述](#概述)
- [整体架构](#整体架构)
- [技术选型](#技术选型)
- [核心功能](#核心功能)
- [架构分层](#架构分层)
- [AI 模型集成](#ai-模型集成)
- [实现方案](#实现方案)
- [配置示例](#配置示例)
- [API 设计](#api-设计)
- [部署方案](#部署方案)

---

## 概述

### 项目升级目标

通过集成 **Spring AI** 框架，实现基于自然语言的智能作业管理能力，让用户可以通过对话式交互来创建、修改和管理数据作业，大幅降低使用门槛。

### 核心价值

- 🗣️ **自然语言交互** - 用户用人话描述需求，系统自动生成作业
- 🤖 **多模型支持** - 集成 DeepSeek R1 和阿里百炼，智能路由选择最优模型
- 🧠 **智能理解** - 理解业务意图，自动补全配置参数
- 📝 **SQL 生成** - 根据需求自动生成数据处理 SQL
- ✅ **智能校验** - AI 辅助检查配置合理性和 SQL 正确性

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                         用户层                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Web UI   │  │ API      │  │ CLI      │  │ 钉钉机器人 │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      AI 服务层                                │
│  ┌────────────────────────────────────────────────────┐     │
│  │            Spring AI Orchestration                  │     │
│  │  ┌──────────────┐  ┌──────────────┐               │     │
│  │  │ Chat Service │  │ Prompt Mgmt  │               │     │
│  │  └──────────────┘  └──────────────┘               │     │
│  │  ┌──────────────┐  ┌──────────────┐               │     │
│  │  │ RAG Engine   │  │ Vector Store │               │     │
│  │  └──────────────┘  └──────────────┘               │     │
│  └────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      AI 模型层                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  DeepSeek R1 │  │ 阿里百炼      │  │  其他模型     │     │
│  │  (推理优先)   │  │ (通用对话)    │  │  (备选)      │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      业务服务层                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ 作业服务  │  │ 数据源    │  │ 版本管理  │  │ 执行引擎  │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      数据持久层                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │  MySQL   │  │ Vector DB│  │  Redis   │                 │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 技术选型

### 核心框架

| 技术栈 | 版本 | 用途 |
|--------|------|------|
| Spring AI | 1.0.0+ | AI 能力集成框架 |
| Spring Boot | 3.2.0+ | 应用基础框架 |
| DeepSeek R1 | Latest | 推理和代码生成 |
| 阿里百炼 (DashScope) | Latest | 通用对话和理解 |

### AI 能力组件

| 组件 | 说明 |
|------|------|
| ChatClient | 统一的对话接口 |
| PromptTemplate | 提示词模板管理 |
| VectorStore | 向量数据库（存储历史案例） |
| DocumentReader | 文档读取和解析 |
| OutputParser | 结构化输出解析 |

---

## 核心功能

### 1. 自然语言创建作业

**用户输入示例：**
```
"帮我创建一个作业，从 MySQL 的 user 表读取数据，
筛选出最近30天注册的用户，写入到 Hive 的 dw_user 表"
```

**AI 处理流程：**
1. 理解意图：创建数据同步作业
2. 提取实体：
   - 源：MySQL.user
   - 目标：Hive.dw_user
   - 条件：注册时间 >= 当前时间-30天
3. 生成 SQL：
```sql
SELECT * FROM user 
WHERE register_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
```
4. 生成作业配置
5. 返回预览，等待用户确认

### 2. 自然语言修改作业

**用户输入示例：**
```
"把作业 JOB001 的筛选条件改成最近7天，
并且增加一个条件：用户状态为激活"
```

**AI 处理流程：**
1. 查询现有作业配置
2. 理解修改意图
3. 生成新的 SQL
4. 展示 diff 对比
5. 用户确认后更新

### 3. SQL 智能生成

**用户输入示例：**
```
"生成一个 SQL，统计每个部门的员工数量和平均工资，
按部门名称排序"
```

**AI 返回：**
```sql
SELECT 
    department,
    COUNT(*) as emp_count,
    AVG(salary) as avg_salary
FROM employees
GROUP BY department
ORDER BY department
```

### 4. 智能问答和建议

- 询问作业状态
- 故障诊断建议
- 性能优化建议
- 最佳实践推荐

---

## 架构分层

### 1. 接口层 (Controller)

```java
@RestController
@RequestMapping("/api/ai")
public class AIAssistantController {
    
    // 对话接口
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request);
    
    // 创建作业
    @PostMapping("/create-job")
    public JobCreationResponse createJob(@RequestBody NaturalLanguageRequest request);
    
    // 修改作业
    @PostMapping("/modify-job")
    public JobModificationResponse modifyJob(@RequestBody JobModificationRequest request);
    
    // SQL 生成
    @PostMapping("/generate-sql")
    public SqlGenerationResponse generateSql(@RequestBody SqlRequest request);
}
```

### 2. AI 服务层 (Service)

```java
@Service
public class AIJobAssistantService {
    
    private final ChatClient deepseekClient;
    private final ChatClient dashscopeClient;
    private final PromptTemplateService promptService;
    private final VectorStoreService vectorStore;
    
    // 智能创建作业
    public Job createJobFromNaturalLanguage(String userInput);
    
    // 智能修改作业
    public Job modifyJobFromNaturalLanguage(Long jobId, String userInput);
    
    // SQL 生成
    public String generateSqlFromNaturalLanguage(String requirement);
    
    // 配置建议
    public ConfigSuggestion suggestConfiguration(JobContext context);
}
```

### 3. 提示词管理层

```java
@Component
public class PromptTemplateService {
    
    // 作业创建提示词
    public PromptTemplate getJobCreationPrompt();
    
    // SQL 生成提示词
    public PromptTemplate getSqlGenerationPrompt();
    
    // 配置提取提示词
    public PromptTemplate getConfigExtractionPrompt();
}
```

### 4. 模型路由层

```java
@Component
public class ModelRouter {
    
    // 根据任务类型选择最优模型
    public ChatClient selectModel(TaskType taskType) {
        return switch (taskType) {
            case SQL_GENERATION -> deepseekClient;  // DeepSeek 擅长代码
            case GENERAL_CHAT -> dashscopeClient;    // 百炼通用对话
            case REASONING -> deepseekClient;        // DeepSeek R1 推理
            default -> dashscopeClient;
        };
    }
}
```

---

## AI 模型集成

### DeepSeek R1 集成

**优势：**
- 🚀 推理能力强
- 💻 代码生成优秀
- 💰 成本效益高
- 🔍 SQL 生成准确

**适用场景：**
- SQL 生成
- 代码生成
- 复杂推理
- 技术问题解答

### 阿里百炼 (DashScope) 集成

**优势：**
- 🇨🇳 中文理解好
- 🎯 通用对话流畅
- 🛡️ 合规性强
- 🏢 企业支持完善

**适用场景：**
- 通用对话
- 意图理解
- 实体提取
- 业务咨询

---

## 实现方案

### Phase 1: 基础能力建设（Week 1-2）

#### 1.1 添加 Spring AI 依赖

```xml
<dependencies>
    <!-- Spring AI Core -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- DeepSeek -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- 阿里百炼 -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-cloud-starter-alibaba-ai</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Vector Store -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

#### 1.2 配置文件

```yaml
# application.yml
spring:
  ai:
    # DeepSeek 配置
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-reasoner
          temperature: 0.7
          max-tokens: 4000
    
    # 阿里百炼配置
    dashscope:
      api-key: ${ALI_AI_KEY}
      chat:
        options:
          model: qwen-max
          temperature: 0.7
    
    # 向量数据库配置
    vectorstore:
      pgvector:
        host: localhost
        port: 5432
        database: vector_db
        dimension: 1536
```

#### 1.3 创建配置类

```java
@Configuration
public class AIConfig {
    
    @Bean
    @Qualifier("deepseek")
    public ChatClient deepseekChatClient(
            @Value("${spring.ai.deepseek.api-key}") String apiKey) {
        return ChatClient.builder()
                .apiKey(apiKey)
                .model("deepseek-reasoner")
                .build();
    }
    
    @Bean
    @Qualifier("dashscope")
    public ChatClient dashscopeChatClient(
            @Value("${spring.ai.dashscope.api-key}") String apiKey) {
        return ChatClient.builder()
                .apiKey(apiKey)
                .model("qwen-max")
                .build();
    }
    
    @Bean
    public VectorStore vectorStore(DataSource dataSource) {
        return new PgVectorStore(dataSource);
    }
}
```

### Phase 2: 核心功能开发（Week 3-4）

#### 2.1 提示词模板

```java
@Component
public class JobPromptTemplates {
    
    public static final String JOB_CREATION_TEMPLATE = """
        你是一个数据平台的智能助手。用户想要创建一个数据作业。
        
        用户需求：{user_input}
        
        可用数据源列表：
        {datasources}
        
        请分析用户需求，提取以下信息并以 JSON 格式返回：
        {{
          "jobName": "作业名称",
          "jobType": "作业类型(DB2HIVE/HIVE2HIVE/SPARK_SQL等)",
          "sourceDatasourceId": "源数据源ID",
          "sourceDatabase": "源数据库",
          "sourceTable": "源表名",
          "targetDatabase": "目标数据库",
          "targetTable": "目标表名",
          "sqlContent": "生成的SQL语句",
          "description": "作业描述"
        }}
        
        如果信息不足，请在 "missingInfo" 字段中列出需要用户补充的信息。
        """;
    
    public static final String SQL_GENERATION_TEMPLATE = """
        你是一个 SQL 专家。请根据用户需求生成 SQL 语句。
        
        用户需求：{requirement}
        数据库类型：{db_type}
        表结构：{table_schema}
        
        请生成标准的 SQL 语句，要求：
        1. 语法正确
        2. 性能优化
        3. 包含必要的注释
        4. 符合最佳实践
        
        只返回 SQL 语句，不要有其他说明文字。
        """;
}
```

#### 2.2 AI 服务实现

```java
@Service
@Slf4j
public class AIJobAssistantService {
    
    @Autowired
    @Qualifier("deepseek")
    private ChatClient deepseekClient;
    
    @Autowired
    @Qualifier("dashscope")
    private ChatClient dashscopeClient;
    
    @Autowired
    private PromptTemplateService promptService;
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private JobService jobService;
    
    /**
     * 从自然语言创建作业
     */
    public JobCreationResult createJobFromNaturalLanguage(String userInput) {
        // 1. 获取可用数据源
        List<DataSource> dataSources = dataSourceService.listAll();
        String datasourceInfo = formatDataSources(dataSources);
        
        // 2. 构建提示词
        PromptTemplate template = new PromptTemplate(
            JobPromptTemplates.JOB_CREATION_TEMPLATE);
        Map<String, Object> params = Map.of(
            "user_input", userInput,
            "datasources", datasourceInfo
        );
        
        // 3. 调用 AI（使用百炼进行意图理解）
        String response = dashscopeClient.call(
            template.render(params)
        );
        
        // 4. 解析 AI 返回的 JSON
        JobConfig config = parseJobConfig(response);
        
        // 5. 如果信息不完整，返回需要补充的信息
        if (config.hasMissingInfo()) {
            return JobCreationResult.needMoreInfo(config.getMissingInfo());
        }
        
        // 6. 使用 DeepSeek 生成 SQL
        if (config.needsSqlGeneration()) {
            String sql = generateSql(config);
            config.setSqlContent(sql);
        }
        
        // 7. 创建作业（草稿状态）
        Job job = jobService.createFromConfig(config);
        
        return JobCreationResult.success(job, config);
    }
    
    /**
     * 生成 SQL
     */
    private String generateSql(JobConfig config) {
        PromptTemplate template = new PromptTemplate(
            JobPromptTemplates.SQL_GENERATION_TEMPLATE);
        
        Map<String, Object> params = Map.of(
            "requirement", config.getRequirement(),
            "db_type", config.getDatabaseType(),
            "table_schema", config.getTableSchema()
        );
        
        // 使用 DeepSeek 生成 SQL
        return deepseekClient.call(template.render(params));
    }
}
```

### Phase 3: 高级功能（Week 5-6）

#### 3.1 RAG（检索增强生成）

```java
@Service
public class RAGService {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private EmbeddingClient embeddingClient;
    
    /**
     * 存储历史案例
     */
    public void storeJobExample(Job job) {
        Document doc = new Document(
            job.toString(),
            Map.of(
                "jobId", job.getId(),
                "jobType", job.getJobType(),
                "createTime", job.getCreateTime()
            )
        );
        vectorStore.add(List.of(doc));
    }
    
    /**
     * 检索相似案例
     */
    public List<Job> findSimilarJobs(String userInput) {
        List<Document> similar = vectorStore.similaritySearch(
            SearchRequest.query(userInput).withTopK(3)
        );
        return similar.stream()
            .map(doc -> convertToJob(doc))
            .collect(Collectors.toList());
    }
}
```

#### 3.2 对话历史管理

```java
@Service
public class ConversationService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String CONVERSATION_KEY = "ai:conversation:";
    
    /**
     * 保存对话历史
     */
    public void saveMessage(String userId, ChatMessage message) {
        String key = CONVERSATION_KEY + userId;
        redisTemplate.opsForList().rightPush(key, message);
        // 保留最近20条对话
        redisTemplate.opsForList().trim(key, -20, -1);
        // 设置过期时间24小时
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
    }
    
    /**
     * 获取对话历史
     */
    public List<ChatMessage> getHistory(String userId) {
        String key = CONVERSATION_KEY + userId;
        return (List<ChatMessage>) redisTemplate
            .opsForList()
            .range(key, 0, -1);
    }
}
```

---

## 配置示例

### 完整的 application.yml

```yaml
spring:
  application:
    name: data-platform-ai
  
  # 数据库配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/data_platform
    username: root
    password: ${DB_PASSWORD}
  
  # Redis 配置（对话历史）
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD}
  
  # AI 配置
  ai:
    # DeepSeek 配置
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-reasoner
          temperature: 0.7
          max-tokens: 4000
          top-p: 0.95
    
    # 阿里百炼配置
    dashscope:
      api-key: ${ALI_AI_KEY}
      chat:
        options:
          model: qwen-max
          temperature: 0.7
          top-p: 0.8
    
    # 向量数据库
    vectorstore:
      pgvector:
        host: localhost
        port: 5432
        database: vector_db
        username: postgres
        password: ${PGVECTOR_PASSWORD}
        dimension: 1536
    
    # RAG 配置
    rag:
      enabled: true
      top-k: 3
      similarity-threshold: 0.7

# AI 助手配置
ai-assistant:
  # 启用的功能
  features:
    job-creation: true
    job-modification: true
    sql-generation: true
    intelligent-qa: true
  
  # 模型路由规则
  model-routing:
    sql-generation: deepseek
    code-generation: deepseek
    reasoning: deepseek
    general-chat: dashscope
    intent-understanding: dashscope
  
  # 提示词配置
  prompts:
    max-length: 4000
    include-examples: true
    include-history: true

# 日志配置
logging:
  level:
    com.dataplatform.ai: debug
    org.springframework.ai: debug
```

### 环境变量配置

创建 `.env` 文件：
```bash
# DeepSeek API Key
DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxx

# 阿里百炼 API Key
ALI_AI_KEY=sk-xxxxxxxxxxxxx

# 数据库密码
DB_PASSWORD=your_password

# Redis 密码
REDIS_PASSWORD=your_redis_password

# 向量数据库密码
PGVECTOR_PASSWORD=your_pgvector_password
```

---

## API 设计

### 1. 对话接口

**请求：**
```http
POST /api/ai/chat
Content-Type: application/json

{
  "message": "帮我创建一个作业，从MySQL读取user表数据写入Hive",
  "conversationId": "conv_123456",
  "context": {
    "currentPage": "job-list"
  }
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "reply": "好的，我来帮你创建这个作业。请确认以下信息...",
    "actions": [
      {
        "type": "CREATE_JOB",
        "config": {
          "jobName": "user_sync_job",
          "sourceTable": "user",
          "targetTable": "dw_user"
        }
      }
    ],
    "suggestions": [
      "需要筛选条件吗？",
      "写入模式是覆盖还是追加？"
    ]
  }
}
```

### 2. 快速创建作业

**请求：**
```http
POST /api/ai/create-job
Content-Type: application/json

{
  "naturalLanguage": "从MySQL的order表读取今天的订单，按用户ID分组统计金额，写入Hive的order_summary表",
  "autoExecute": false
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "jobPreview": {
      "jobName": "order_daily_summary",
      "jobType": "DB2HIVE",
      "sqlContent": "SELECT user_id, SUM(amount) as total_amount...",
      "confidence": 0.95
    },
    "needsConfirmation": true,
    "questions": []
  }
}
```

### 3. SQL 生成

**请求：**
```http
POST /api/ai/generate-sql
Content-Type: application/json

{
  "requirement": "统计每个部门的员工平均工资，只显示平均工资超过10000的部门",
  "databaseType": "MySQL",
  "tableInfo": {
    "tableName": "employees",
    "columns": ["emp_id", "name", "department", "salary"]
  }
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "sql": "SELECT department, AVG(salary) as avg_salary\nFROM employees\nGROUP BY department\nHAVING AVG(salary) > 10000",
    "explanation": "这个SQL使用GROUP BY按部门分组，AVG计算平均工资，HAVING筛选平均工资大于10000的部门",
    "optimizationTips": [
      "建议在department字段上创建索引以提高查询性能"
    ]
  }
}
```

---

## 部署方案

### Docker Compose 部署

```yaml
version: '3.8'

services:
  # 应用服务
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - ALI_AI_KEY=${ALI_AI_KEY}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - mysql
      - redis
      - pgvector
  
  # MySQL 数据库
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: data_platform
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
  
  # Redis（对话历史）
  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis-data:/data
  
  # PGVector（向量数据库）
  pgvector:
    image: ankane/pgvector:latest
    environment:
      POSTGRES_DB: vector_db
      POSTGRES_PASSWORD: ${PGVECTOR_PASSWORD}
    volumes:
      - pgvector-data:/var/lib/postgresql/data

volumes:
  mysql-data:
  redis-data:
  pgvector-data:
```

---

## 总结

### 技术亮点

- 🤖 **多模型协同** - DeepSeek + 阿里百炼，各取所长
- 🧠 **智能理解** - 准确理解用户意图，减少交互次数
- 📝 **代码生成** - 自动生成高质量 SQL 和配置
- 🔍 **RAG 增强** - 基于历史案例提供更准确的建议
- 💬 **对话式交互** - 自然流畅的用户体验

### 预期效果

- ⏱️ **效率提升 80%** - 从填表到说话，大幅降低操作时间
- 📉 **错误率降低 60%** - AI 辅助校验，减少配置错误
- 👥 **使用门槛降低** - 不懂技术的人也能创建作业
- 🎯 **准确率 > 90%** - 多轮对话确保需求理解准确

### 后续规划

- 📱 语音输入支持
- 🎨 可视化流程编排
- 🔄 自动化运维建议
- 📊 智能报表生成

---

**立即开始升级你的数据平台！** 🚀
