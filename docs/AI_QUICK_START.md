# 🚀 AI 增强功能快速实施指南

## 📋 实施步骤

### 第一步：环境准备（30分钟）

#### 1.1 获取 API Key

**DeepSeek:**
1. 访问 https://platform.deepseek.com
2. 注册账号并登录
3. 进入 API Keys 页面
4. 创建新的 API Key
5. 保存 Key（格式：`sk-xxxxxxxxxx`）

**阿里百炼:**
1. 访问 https://dashscope.aliyun.com
2. 登录阿里云账号
3. 开通 DashScope 服务
4. 创建 API Key
5. 保存 Key

#### 1.2 安装向量数据库（可选）

```bash
# 使用 Docker 快速启动 PGVector
docker run -d \
  --name pgvector \
  -e POSTGRES_PASSWORD=yourpassword \
  -e POSTGRES_DB=vector_db \
  -p 5432:5432 \
  ankane/pgvector:latest
```

### 第二步：添加依赖（10分钟）

在 `pom.xml` 中添加：

```xml
<properties>
    <spring-ai.version>1.0.0-M3</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring AI Core -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Spring AI Alibaba（阿里百炼） -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter</artifactId>
        <version>1.0.0-M2</version>
    </dependency>
    
    <!-- Vector Store（可选） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 第三步：配置文件（5分钟）

在 `application.yml` 中添加：

```yaml
spring:
  ai:
    # DeepSeek 配置（作为 OpenAI 兼容接口）
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-reasoner
          temperature: 0.7
    
    # 阿里百炼配置
    alibaba:
      api-key: ${ALI_AI_KEY}
      chat:
        options:
          model: qwen-max
          temperature: 0.7
```

创建 `.env` 文件（不要提交到 Git）：

```bash
DEEPSEEK_API_KEY=sk-your-deepseek-key
ALI_AI_KEY=sk-your-alibaba-key
```

### 第四步：创建核心类（2小时）

#### 4.1 AI 配置类

创建 `src/main/java/com/dataplatform/config/AIConfig.java`：

```java
package com.dataplatform.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {
    
    @Bean
    @Qualifier("deepseek")
    public ChatClient deepseekChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
    
    @Bean
    @Qualifier("alibaba")
    public ChatClient alibabaChatClient(
            @Qualifier("alibabaChatClientBuilder") ChatClient.Builder builder) {
        return builder.build();
    }
}
```

#### 4.2 AI 助手服务

创建 `src/main/java/com/dataplatform/service/AIAssistantService.java`：

```java
package com.dataplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIAssistantService {
    
    @Qualifier("deepseek")
    private final ChatClient deepseekClient;
    
    @Qualifier("alibaba")
    private final ChatClient alibabaClient;
    
    /**
     * 通用对话
     */
    public String chat(String userMessage) {
        return alibabaClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
    
    /**
     * 生成 SQL
     */
    public String generateSql(String requirement) {
        String template = """
            你是一个 SQL 专家。请根据用户需求生成 SQL 语句。
            
            用户需求：{requirement}
            
            要求：
            1. 生成标准的 SQL 语句
            2. 添加必要的注释
            3. 考虑性能优化
            4. 只返回 SQL，不要其他说明
            """;
        
        PromptTemplate promptTemplate = new PromptTemplate(template);
        
        return deepseekClient.prompt()
                .user(u -> u.text(promptTemplate.render(Map.of("requirement", requirement))))
                .call()
                .content();
    }
    
    /**
     * 提取作业配置
     */
    public String extractJobConfig(String userInput) {
        String template = """
            用户想要创建一个数据作业，请从以下描述中提取关键信息：
            
            用户描述：{userInput}
            
            请以 JSON 格式返回：
            {
              "jobName": "作业名称",
              "sourceTable": "源表",
              "targetTable": "目标表",
              "filterCondition": "筛选条件",
              "needsAggregation": true/false
            }
            
            只返回 JSON，不要其他内容。
            """;
        
        PromptTemplate promptTemplate = new PromptTemplate(template);
        
        return alibabaClient.prompt()
                .user(u -> u.text(promptTemplate.render(Map.of("userInput", userInput))))
                .call()
                .content();
    }
}
```

#### 4.3 AI 控制器

创建 `src/main/java/com/dataplatform/controller/AIController.java`：

```java
package com.dataplatform.controller;

import com.dataplatform.common.Result;
import com.dataplatform.dto.ChatRequest;
import com.dataplatform.dto.SqlGenerationRequest;
import com.dataplatform.service.AIAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI 助手", description = "AI 智能助手接口")
public class AIController {
    
    private final AIAssistantService aiAssistantService;
    
    @PostMapping("/chat")
    @Operation(summary = "通用对话")
    public Result<String> chat(@RequestBody ChatRequest request) {
        String response = aiAssistantService.chat(request.getMessage());
        return Result.success(response);
    }
    
    @PostMapping("/generate-sql")
    @Operation(summary = "生成 SQL")
    public Result<String> generateSql(@RequestBody SqlGenerationRequest request) {
        String sql = aiAssistantService.generateSql(request.getRequirement());
        return Result.success(sql);
    }
    
    @PostMapping("/extract-config")
    @Operation(summary = "提取作业配置")
    public Result<String> extractConfig(@RequestBody ChatRequest request) {
        String config = aiAssistantService.extractJobConfig(request.getMessage());
        return Result.success(config);
    }
}
```

#### 4.4 DTO 类

创建 `src/main/java/com/dataplatform/dto/ChatRequest.java`：

```java
package com.dataplatform.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
}
```

创建 `src/main/java/com/dataplatform/dto/SqlGenerationRequest.java`：

```java
package com.dataplatform.dto;

import lombok.Data;

@Data
public class SqlGenerationRequest {
    private String requirement;
    private String databaseType;
    private String tableName;
}
```

### 第五步：测试（30分钟）

#### 5.1 启动应用

```bash
# 设置环境变量
export DEEPSEEK_API_KEY=sk-your-key
export ALI_AI_KEY=sk-your-key

# 启动应用
mvn spring-boot:run
```

#### 5.2 测试 API

**测试通用对话：**
```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好，你能做什么？"
  }'
```

**测试 SQL 生成：**
```bash
curl -X POST http://localhost:8080/api/ai/generate-sql \
  -H "Content-Type: application/json" \
  -d '{
    "requirement": "查询订单表中今天的所有订单，按金额降序排列",
    "databaseType": "MySQL",
    "tableName": "orders"
  }'
```

**测试配置提取：**
```bash
curl -X POST http://localhost:8080/api/ai/extract-config \
  -H "Content-Type: application/json" \
  -d '{
    "message": "帮我创建一个作业，从 MySQL 的 user 表读取数据，筛选最近30天注册的用户，写入 Hive 的 dw_user 表"
  }'
```

## 📊 测试检查清单

- [ ] DeepSeek API Key 配置正确
- [ ] 阿里百炼 API Key 配置正确
- [ ] 应用启动成功
- [ ] `/api/ai/chat` 接口返回正常
- [ ] `/api/ai/generate-sql` 接口生成 SQL 正确
- [ ] `/api/ai/extract-config` 接口提取配置正确
- [ ] Swagger 文档更新（访问 `/swagger-ui.html`）

## 🎯 下一步增强

### 阶段 2：高级功能（1-2周）

1. **对话历史管理**
   - 使用 Redis 存储对话上下文
   - 支持多轮对话
   - 上下文感知回复

2. **RAG 增强**
   - 存储历史作业案例到向量数据库
   - 检索相似案例辅助决策
   - 提供更准确的建议

3. **智能作业创建**
   - 一句话创建完整作业
   - 自动补全配置参数
   - 智能生成 SQL

4. **作业修改助手**
   - 自然语言修改作业配置
   - 智能 diff 对比
   - 风险提示

### 阶段 3：生产优化（1周）

1. **性能优化**
   - 缓存常用提示词
   - 异步处理长任务
   - 流式输出支持

2. **安全增强**
   - API Key 加密存储
   - 请求限流
   - 内容安全过滤

3. **监控告警**
   - AI 调用统计
   - 成本监控
   - 异常告警

## 💰 成本估算

### DeepSeek 定价（参考）
- 输入：¥1 / 百万 tokens
- 输出：¥2 / 百万 tokens

### 阿里百炼定价（参考）
- Qwen-Max：¥0.12 / 千 tokens

### 月度成本估算（中等使用量）
- 假设每天 1000 次对话
- 平均每次 500 tokens
- 月成本约：¥300-500

## 🔒 安全建议

1. **API Key 管理**
   ```bash
   # 使用环境变量，不要硬编码
   export DEEPSEEK_API_KEY=xxx
   export ALI_AI_KEY=xxx
   ```

2. **访问控制**
   - AI 接口需要认证
   - 限制每用户调用频率
   - 记录审计日志

3. **内容过滤**
   - 敏感词过滤
   - SQL 注入检测
   - 异常输入拦截

## 📚 参考资源

- **Spring AI 文档**: https://docs.spring.io/spring-ai/reference/
- **DeepSeek 文档**: https://platform.deepseek.com/docs
- **阿里百炼文档**: https://help.aliyun.com/zh/dashscope/

## 🎉 完成！

现在你的数据平台已经具备 AI 能力了！

**快速验证：**
1. 访问 Swagger UI: `http://localhost:8080/swagger-ui.html`
2. 找到 "AI 助手" 分组
3. 测试 `/api/ai/chat` 接口
4. 看到 AI 回复就成功了！🚀

---

**有问题？查看完整架构文档：**
`docs/AI_ARCHITECTURE.md`
