# 创建 NLPromptTemplates.java 类

## 任务概述
在 `src/main/java/com/dataplatform/ai/` 目录下创建 `NLPromptTemplates.java`，实现自然语言处理的提示词模板类。

## 参考文件分析
已读取 `PromptTemplates.java`，确认实现风格：
- 使用 `public static final String` 定义模板
- 使用 Java 文本块（`"""`）语法
- 包含清晰的 JavaDoc 注释
- 模板中使用 `{variable}` 作为占位符
- 返回格式主要是 JSON

## 需要实现的模板

### 1. INTENT_RECOGNITION_TEMPLATE
**用途**：识别用户意图
**识别类型**：
- CREATE_JOB（创建作业）
- UPDATE_JOB（修改作业）
- PUBLISH_JOB（发布作业）
- DELETE_JOB（删除作业）
- QUERY_JOB（查询作业）
- CHAT（普通对话）

**输入变量**：`{user_input}`
**输出格式**：JSON，包含 `intent` 和 `confidence` 字段

### 2. EXTRACT_CREATE_JOB_PARAMS_TEMPLATE
**用途**：提取创建作业所需的参数
**提取字段**：
- jobCode, jobName, jobType
- sourceDatabase, sourceTable
- targetDatabase, targetTable
- engineType, description
- sourceDatasourceId（DB2HIVE 类型需要）

**输入变量**：`{user_input}`, `{datasources}`
**输出格式**：JSON，包含上述字段和可能的 `missingInfo` 字段

### 3. EXTRACT_UPDATE_JOB_PARAMS_TEMPLATE
**用途**：提取修改作业的参数
**输入变量**：`{user_input}`, `{existing_job_info}`
**输出格式**：JSON，包含要修改的字段

### 4. EXTRACT_PUBLISH_JOB_PARAMS_TEMPLATE
**用途**：提取发布作业的参数
**输入变量**：`{user_input}`
**输出格式**：JSON，包含 jobCode 或 jobName

### 5. EXTRACT_DELETE_JOB_PARAMS_TEMPLATE
**用途**：提取删除作业的参数
**输入变量**：`{user_input}`
**输出格式**：JSON，包含 jobCode 或 jobName

### 6. GENERATE_QUESTION_TEMPLATE
**用途**：生成询问用户的问题
**输入变量**：`{missing_info}`, `{context}`
**输出格式**：自然语言问题

### 7. CONFIRM_OPERATION_TEMPLATE
**用途**：生成操作确认信息
**输入变量**：`{operation}`, `{params}`
**输出格式**：自然语言确认信息

## 实现要点
1. 遵循现有代码风格，使用文本块语法
2. 每个模板添加清晰的 JavaDoc 注释
3. 模板中明确指定返回格式（JSON 或自然语言）
4. 考虑中文场景，提示词使用中文
5. 包含足够的上下文说明，让 AI 理解任务
6. 对于 JSON 返回，明确要求"只返回 JSON，不要有其他说明文字"

## 文件位置
`D:\alert4\src\main\java\com\dataplatform\ai\NLPromptTemplates.java`

## 模板设计详情

### INTENT_RECOGNITION_TEMPLATE
```java
public static final String INTENT_RECOGNITION_TEMPLATE = """
    你是一个数据平台的智能助手。请识别用户的意图。
    
    用户输入：{user_input}
    
    可能的意图类型：
    - CREATE_JOB：创建新的数据作业
    - UPDATE_JOB：修改现有作业的配置
    - PUBLISH_JOB：发布作业到生产环境
    - DELETE_JOB：删除作业
    - QUERY_JOB：查询作业信息或状态
    - CHAT：普通对话、咨询或其他非操作类交互
    
    请以 JSON 格式返回：
    {
      "intent": "意图类型",
      "confidence": 0.95
    }
    
    只返回 JSON，不要有其他说明文字。
    """;
```

### EXTRACT_CREATE_JOB_PARAMS_TEMPLATE
```java
public static final String EXTRACT_CREATE_JOB_PARAMS_TEMPLATE = """
    你是一个数据平台的智能助手。用户想要创建一个数据作业，请提取关键参数。
    
    用户输入：{user_input}
    
    可用数据源列表：
    {datasources}
    
    作业类型说明：
    - DB2HIVE：从关系数据库（DB2/MySQL/Oracle等）导入到 Hive，需要 sourceDatasourceId
    - HIVE2HIVE：Hive 表之间的数据转换
    - SPARK_SQL：使用 Spark SQL 进行数据处理
    
    请分析用户需求，提取以下信息并以 JSON 格式返回：
    {
      "jobCode": "作业编码（唯一标识）",
      "jobName": "作业名称",
      "jobType": "作业类型（DB2HIVE/HIVE2HIVE/SPARK_SQL）",
      "sourceDatabase": "源数据库名",
      "sourceTable": "源表名",
      "targetDatabase": "目标数据库名",
      "targetTable": "目标表名",
      "engineType": "引擎类型（HIVE/SPARK）",
      "description": "作业描述",
      "sourceDatasourceId": "源数据源ID（仅DB2HIVE类型需要）"
    }
    
    如果某些信息无法从用户输入中获取，请在 "missingInfo" 字段中列出：
    {
      "missingInfo": ["缺失字段1", "缺失字段2"]
    }
    
    只返回 JSON，不要有其他说明文字。
    """;
```

### EXTRACT_UPDATE_JOB_PARAMS_TEMPLATE
```java
public static final String EXTRACT_UPDATE_JOB_PARAMS_TEMPLATE = """
    你是一个数据平台的智能助手。用户想要修改现有作业，请提取修改参数。
    
    用户输入：{user_input}
    
    现有作业信息：
    {existing_job_info}
    
    请分析用户想要修改哪些字段，以 JSON 格式返回：
    {
      "jobCode": "要修改的作业编码或名称",
      "updates": {
        "fieldName1": "新值1",
        "fieldName2": "新值2"
      }
    }
    
    可修改的字段包括：jobName, description, sourceTable, targetTable, sqlContent 等。
    
    如果无法确定要修改哪个作业或修改什么内容，请在 "missingInfo" 中说明。
    
    只返回 JSON，不要有其他说明文字。
    """;
```

### EXTRACT_PUBLISH_JOB_PARAMS_TEMPLATE
```java
public static final String EXTRACT_PUBLISH_JOB_PARAMS_TEMPLATE = """
    你是一个数据平台的智能助手。用户想要发布作业，请提取相关参数。
    
    用户输入：{user_input}
    
    请识别用户想要发布的作业，以 JSON 格式返回：
    {
      "jobCode": "作业编码",
      "jobName": "作业名称"
    }
    
    用户可能通过作业编码或作业名称来指定作业，请尽量提取。
    
    如果无法确定要发布哪个作业，请在 "missingInfo" 中说明。
    
    只返回 JSON，不要有其他说明文字。
    """;
```

### EXTRACT_DELETE_JOB_PARAMS_TEMPLATE
```java
public static final String EXTRACT_DELETE_JOB_PARAMS_TEMPLATE = """
    你是一个数据平台的智能助手。用户想要删除作业，请提取相关参数。
    
    用户输入：{user_input}
    
    请识别用户想要删除的作业，以 JSON 格式返回：
    {
      "jobCode": "作业编码",
      "jobName": "作业名称"
    }
    
    用户可能通过作业编码或作业名称来指定作业，请尽量提取。
    
    如果无法确定要删除哪个作业，请在 "missingInfo" 中说明。
    
    只返回 JSON，不要有其他说明文字。
    """;
```

### GENERATE_QUESTION_TEMPLATE
```java
public static final String GENERATE_QUESTION_TEMPLATE = """
    你是一个数据平台的智能助手。用户提供的信息不完整，需要询问用户补充。
    
    缺失的信息：{missing_info}
    当前上下文：{context}
    
    请生成一个友好、专业的问题来询问用户，要求：
    1. 语气自然、礼貌
    2. 明确指出需要补充什么信息
    3. 如果可能，提供选项或示例
    4. 一次询问不要超过 3 个问题
    
    直接返回要问用户的问题，不要有其他说明文字。
    """;
```

### CONFIRM_OPERATION_TEMPLATE
```java
public static final String CONFIRM_OPERATION_TEMPLATE = """
    你是一个数据平台的智能助手。请生成操作确认信息。
    
    操作类型：{operation}
    操作参数：{params}
    
    请生成一条清晰的确认信息，要求：
    1. 明确说明将要执行的操作
    2. 列出关键参数和配置
    3. 使用友好、专业的语气
    4. 格式清晰，易于阅读
    
    直接返回确认信息，不要有其他说明文字。
    """;
```

## 实现步骤
1. ✅ 分析现有 PromptTemplates.java 的代码风格
2. ✅ 设计 7 个模板的具体内容
3. ⏳ 创建 NLPromptTemplates.java 文件
4. ⏳ 添加包声明和类注释
5. ⏳ 实现所有模板常量
6. ⏳ 验证代码格式和注释完整性

## 完整的 NLPromptTemplates.java 文件结构

```java
package com.dataplatform.ai;

/**
 * 自然语言处理提示词模板
 * 用于处理用户的自然语言输入，包括意图识别、参数提取、问题生成等
 */
public class NLPromptTemplates {

    /**
     * 意图识别提示词模板
     * 用于识别用户输入的意图类型
     */
    public static final String INTENT_RECOGNITION_TEMPLATE = """
        你是一个数据平台的智能助手。请识别用户的意图。
        
        用户输入：{user_input}
        
        可能的意图类型：
        - CREATE_JOB：创建新的数据作业
        - UPDATE_JOB：修改现有作业的配置
        - PUBLISH_JOB：发布作业到生产环境
        - DELETE_JOB：删除作业
        - QUERY_JOB：查询作业信息或状态
        - CHAT：普通对话、咨询或其他非操作类交互
        
        请以 JSON 格式返回：
        {
          "intent": "意图类型",
          "confidence": 0.95
        }
        
        只返回 JSON，不要有其他说明文字。
        """;

    /**
     * 创建作业参数提取提示词模板
     * 用于从用户输入中提取创建作业所需的参数
     */
    public static final String EXTRACT_CREATE_JOB_PARAMS_TEMPLATE = """
        你是一个数据平台的智能助手。用户想要创建一个数据作业，请提取关键参数。
        
        用户输入：{user_input}
        
        可用数据源列表：
        {datasources}
        
        作业类型说明：
        - DB2HIVE：从关系数据库（DB2/MySQL/Oracle等）导入到 Hive，需要 sourceDatasourceId
        - HIVE2HIVE：Hive 表之间的数据转换
        - SPARK_SQL：使用 Spark SQL 进行数据处理
        
        请分析用户需求，提取以下信息并以 JSON 格式返回：
        {
          "jobCode": "作业编码（唯一标识）",
          "jobName": "作业名称",
          "jobType": "作业类型（DB2HIVE/HIVE2HIVE/SPARK_SQL）",
          "sourceDatabase": "源数据库名",
          "sourceTable": "源表名",
          "targetDatabase": "目标数据库名",
          "targetTable": "目标表名",
          "engineType": "引擎类型（HIVE/SPARK）",
          "description": "作业描述",
          "sourceDatasourceId": "源数据源ID（仅DB2HIVE类型需要）"
        }
        
        如果某些信息无法从用户输入中获取，请在 "missingInfo" 字段中列出：
        {
          "missingInfo": ["缺失字段1", "缺失字段2"]
        }
        
        只返回 JSON，不要有其他说明文字。
        """;

    /**
     * 修改作业参数提取提示词模板
     * 用于从用户输入中提取修改作业所需的参数
     */
    public static final String EXTRACT_UPDATE_JOB_PARAMS_TEMPLATE = """
        你是一个数据平台的智能助手。用户想要修改现有作业，请提取修改参数。
        
        用户输入：{user_input}
        
        现有作业信息：
        {existing_job_info}
        
        请分析用户想要修改哪些字段，以 JSON 格式返回：
        {
          "jobCode": "要修改的作业编码或名称",
          "updates": {
            "fieldName1": "新值1",
            "fieldName2": "新值2"
          }
        }
        
        可修改的字段包括：jobName, description, sourceTable, targetTable, sqlContent 等。
        
        如果无法确定要修改哪个作业或修改什么内容，请在 "missingInfo" 中说明。
        
        只返回 JSON，不要有其他说明文字。
        """;

    /**
     * 发布作业参数提取提示词模板
     * 用于从用户输入中提取发布作业所需的参数
     */
    public static final String EXTRACT_PUBLISH_JOB_PARAMS_TEMPLATE = """
        你是一个数据平台的智能助手。用户想要发布作业，请提取相关参数。
        
        用户输入：{user_input}
        
        请识别用户想要发布的作业，以 JSON 格式返回：
        {
          "jobCode": "作业编码",
          "jobName": "作业名称"
        }
        
        用户可能通过作业编码或作业名称来指定作业，请尽量提取。
        
        如果无法确定要发布哪个作业，请在 "missingInfo" 中说明。
        
        只返回 JSON，不要有其他说明文字。
        """;

    /**
     * 删除作业参数提取提示词模板
     * 用于从用户输入中提取删除作业所需的参数
     */
    public static final String EXTRACT_DELETE_JOB_PARAMS_TEMPLATE = """
        你是一个数据平台的智能助手。用户想要删除作业，请提取相关参数。
        
        用户输入：{user_input}
        
        请识别用户想要删除的作业，以 JSON 格式返回：
        {
          "jobCode": "作业编码",
          "jobName": "作业名称"
        }
        
        用户可能通过作业编码或作业名称来指定作业，请尽量提取。
        
        如果无法确定要删除哪个作业，请在 "missingInfo" 中说明。
        
        只返回 JSON，不要有其他说明文字。
        """;

    /**
     * 问题生成提示词模板
     * 用于在信息不完整时生成询问用户的问题
     */
    public static final String GENERATE_QUESTION_TEMPLATE = """
        你是一个数据平台的智能助手。用户提供的信息不完整，需要询问用户补充。
        
        缺失的信息：{missing_info}
        当前上下文：{context}
        
        请生成一个友好、专业的问题来询问用户，要求：
        1. 语气自然、礼貌
        2. 明确指出需要补充什么信息
        3. 如果可能，提供选项或示例
        4. 一次询问不要超过 3 个问题
        
        直接返回要问用户的问题，不要有其他说明文字。
        """;

    /**
     * 操作确认提示词模板
     * 用于生成操作确认信息，在执行操作前让用户确认
     */
    public static final String CONFIRM_OPERATION_TEMPLATE = """
        你是一个数据平台的智能助手。请生成操作确认信息。
        
        操作类型：{operation}
        操作参数：{params}
        
        请生成一条清晰的确认信息，要求：
        1. 明确说明将要执行的操作
        2. 列出关键参数和配置
        3. 使用友好、专业的语气
        4. 格式清晰，易于阅读
        
        直接返回确认信息，不要有其他说明文字。
        """;
}
```

## 设计说明

### 模板特点
1. **一致性**：与 PromptTemplates.java 保持相同的代码风格
2. **清晰性**：每个模板都有明确的 JavaDoc 注释说明用途
3. **中文化**：所有提示词使用中文，适合中文用户场景
4. **结构化输出**：大部分模板要求返回 JSON 格式，便于程序解析
5. **容错处理**：包含 missingInfo 机制处理信息不完整的情况

### 占位符说明
- `{user_input}`：用户的原始输入
- `{datasources}`：可用数据源列表
- `{existing_job_info}`：现有作业信息
- `{missing_info}`：缺失的信息列表
- `{context}`：当前上下文信息
- `{operation}`：操作类型
- `{params}`：操作参数

### 使用场景
1. **意图识别**：首先判断用户想做什么
2. **参数提取**：根据不同意图提取相应参数
3. **交互式补充**：信息不足时询问用户
4. **操作确认**：执行前让用户确认操作内容

## 后续建议
1. 可以添加更多细化的模板，如查询作业的参数提取
2. 可以根据实际使用情况调整提示词内容
3. 建议配合单元测试验证模板的有效性
