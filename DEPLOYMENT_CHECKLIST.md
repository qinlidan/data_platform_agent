# StateGraph AI Agent 部署检查清单

## ✅ 部署前检查

### 1. 环境准备

- [ ] Java 11+ 已安装
- [ ] Maven 3.6+ 已安装
- [ ] Redis 6.0+ 已启动并可访问
- [ ] OpenAI API Key 已准备（或兼容的API）

### 2. 配置检查

`application.yml` 或 `application.properties`：

```yaml
# OpenAI 配置
openai:
  api:
    key: sk-xxxxxxxxxxxxxx  # 必须配置
    url: https://api.openai.com/v1/chat/completions
  model: gpt-4  # 或 gpt-3.5-turbo

# Redis 配置
spring:
  redis:
    host: localhost  # Redis 地址
    port: 6379
    password:  # 如果有密码
    database: 0
    timeout: 5000ms
```

### 3. 依赖检查

确保 `pom.xml` 包含以下依赖：

```xml
<!-- Spring Boot WebFlux -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- Spring Data Redis Reactive -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- Jackson -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## 🚀 启动步骤

### 1. 启动 Redis

```bash
# Linux/Mac
redis-server

# Docker
docker run -d -p 6379:6379 redis:latest

# 验证
redis-cli ping  # 应返回 PONG
```

### 2. 编译项目

```bash
cd /d/alert4
mvn clean install -DskipTests
```

### 3. 启动应用

```bash
# 方式1：Maven
mvn spring-boot:run

# 方式2：Jar
java -jar target/alert4-1.0.0.jar

# 方式3：IDE
直接运行 Application.java
```

### 4. 验证启动

打开浏览器访问：
- http://localhost:8080/stategraph-test.html

应该看到 AI 助手界面。

---

## 🧪 功能测试

### 1. 基础对话测试

在测试页面输入：
```
查询所有作业
```

预期：
- AI 调用 `searchJobs` 工具
- 返回作业列表

### 2. 审批流程测试

输入：
```
删除作业 job_001
```

预期：
- AI 识别需要审批
- 前端显示审批卡片
- 点击"确认执行"后完成删除

### 3. 多工具调用测试

输入：
```
创建一个名为 test_job 的 ETL 作业，然后查询它的详情
```

预期：
- AI 先调用 `createJob`
- 再调用 `getJobDetail`
- 返回完整结果

### 4. 错误处理测试

输入：
```
删除不存在的作业 job_999
```

预期：
- AI 调用 `deleteJob`
- 返回错误提示

---

## 🔍 日志检查

### 1. 关键日志

启动成功应看到：

```
[ToolRegistry] 开始注册工具...
[ToolRegistry] 工具注册完成
[StateGraph] StateGraph 初始化完成
```

### 2. 调用日志

对话时应看到：

```
[AgentNode] 开始推理，消息数：2
[AgentNode] OpenAI 推理完成，工具调用数：1
[ToolNode] 执行工具：searchJobs
[ToolNode] 工具执行完成，结果：{total=3, rows=[...]}
```

### 3. 审批日志

触发审批时：

```
[AgentNode] 检测到需要审批的工具：deleteJob
[ApprovalService] 保存审批 Checkpoint：1234567890_123
[ApprovalService] 审批 ID=1234567890_123, approved=true
[ApprovalService] 恢复状态，执行工具
```

---

## ⚠️ 常见问题

### 1. Redis 连接失败

**错误信息：**
```
Unable to connect to Redis; nested exception is io.lettuce.core.RedisConnectionException
```

**解决方案：**
- 检查 Redis 是否启动：`redis-cli ping`
- 检查配置：`spring.redis.host` 和 `spring.redis.port`
- 检查防火墙

### 2. OpenAI API 调用失败

**错误信息：**
```
[OpenAI] 调用失败: 401 Unauthorized
```

**解决方案：**
- 检查 API Key 是否正确：`openai.api.key`
- 检查 API URL 是否可访问
- 检查网络代理设置

### 3. 工具未注册

**错误信息：**
```
[ToolExecutor] 工具不存在: createJob
```

**解决方案：**
- 检查 `ToolRegistry` 是否执行
- 检查工具名称拼写
- 检查日志：`[ToolRegistry] 工具注册完成`

### 4. 审批超时

**现象：**
- 点击"确认执行"后提示 Checkpoint 不存在

**原因：**
- Checkpoint TTL 默认 10 分钟，超时自动删除

**解决方案：**
- 调整 `RedisCheckpoint` 中的 TTL
- 或在超时前完成审批

### 5. SSE 连接断开

**现象：**
- 前端接收不到流式数据

**解决方案：**
- 检查浏览器控制台是否有错误
- 检查后端日志是否有异常
- 尝试刷新页面重新连接

---

## 🔧 性能优化建议

### 1. Redis 连接池

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 10
        max-idle: 5
        min-idle: 2
```

### 2. OpenAI 超时设置

```java
WebClient client = WebClient.builder()
    .baseUrl(apiUrl)
    .defaultHeader(...)
    .build()
    .mutate()
    .responseTimeout(Duration.ofSeconds(30))  // 设置超时
    .build();
```

### 3. 日志级别

生产环境建议：

```yaml
logging:
  level:
    com.dataplatform: INFO
    org.springframework: WARN
```

### 4. 监控指标

建议监控：
- OpenAI API 调用次数/成功率
- 工具执行次数/耗时
- 审批响应时间
- Redis 连接状态

---

## 📊 生产环境建议

### 1. 安全加固

- [ ] OpenAI API Key 使用环境变量或密钥管理服务
- [ ] Redis 启用密码认证
- [ ] 添加 API 认证（JWT/OAuth）
- [ ] 添加 API 限流（防止滥用）
- [ ] 审批操作记录审计日志

### 2. 高可用

- [ ] Redis 主从/集群部署
- [ ] 应用多实例部署（负载均衡）
- [ ] 添加健康检查接口
- [ ] 添加熔断降级（Circuit Breaker）

### 3. 可观测性

- [ ] 集成 Prometheus（指标采集）
- [ ] 集成 Grafana（可视化）
- [ ] 集成 ELK（日志聚合）
- [ ] 添加链路追踪（Jaeger/Zipkin）

### 4. 成本控制

- [ ] 限制单次对话轮数
- [ ] 限制工具调用次数
- [ ] 缓存常见查询结果
- [ ] 使用 gpt-3.5-turbo 代替 gpt-4（成本降低 90%）

---

## 📝 维护清单

### 每日

- [ ] 检查 Redis 内存使用率
- [ ] 检查 OpenAI API 调用量
- [ ] 检查错误日志

### 每周

- [ ] 清理过期 Checkpoint
- [ ] 分析工具调用统计
- [ ] 检查审批响应时间

### 每月

- [ ] 更新依赖版本（安全补丁）
- [ ] 优化工具定义（提高识别准确率）
- [ ] 评估 OpenAI 费用

---

## 🎯 下一步

完成部署后，可以：

1. **对接真实服务**：修改 `ToolRegistry` 中的工具实现
2. **添加新工具**：参考 `STATEGRAPH_README.md` 扩展指南
3. **定制审批流程**：修改 `AgentNode` 审批逻辑
4. **优化提示词**：调整 `AgentNode` 的 System Prompt
5. **集成前端**：将测试页面集成到实际应用

---

## 📞 技术支持

如遇到问题，请检查：

1. **日志文件**：`logs/application.log`
2. **Redis 状态**：`redis-cli info`
3. **网络连通性**：`curl https://api.openai.com/v1/models`
4. **项目文档**：`STATEGRAPH_README.md`

---

祝您部署顺利！🎉
