# AI 功能测试指南

## 快速开始

### 1. 设置环境变量

在启动应用前，需要设置 API Keys：

**Windows (CMD):**
```cmd
set DEEPSEEK_API_KEY=你的DeepSeek_API_Key
set ALI_AI_KEY=你的阿里云API_Key
```

**Windows (PowerShell):**
```powershell
$env:DEEPSEEK_API_KEY="你的DeepSeek_API_Key"
$env:ALI_AI_KEY="你的阿里云API_Key"
```

**Linux/Mac:**
```bash
export DEEPSEEK_API_KEY=你的DeepSeek_API_Key
export ALI_AI_KEY=你的阿里云API_Key
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

或在 IDEA 中直接运行 `DataPlatformApplication`

### 3. 验证服务启动

访问健康检查接口：
```
http://localhost:8080/api/ai/health
```

期望返回：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "service": "AI Assistant",
    "models": {
      "deepseek": "deepseek-chat",
      "alibaba": "qwen-max"
    }
  }
}
```

---

## 功能测试

### 1. 通用对话测试

**接口:** `POST /api/ai/chat`

**请求示例:**
```json
{
  "message": "你好，我想创建一个数据同步作业",
  "userId": 1
}
```

**cURL 命令:**
```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，我想创建一个数据同步作业","userId":1}'
```

**期望响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "reply": "您好！我可以帮您创建数据同步作业。请告诉我：\n1. 源数据库和表名\n2. 目标数据库和表名\n3. 是否需要数据转换\n4. 同步频率要求",
    "conversationId": "conv_abc123"
  }
}
```

---

### 2. SQL 生成测试

**接口:** `POST /api/ai/generate-sql`

**请求示例:**
```json
{
  "requirement": "查询所有订单，按金额降序排列，只要最近7天的数据",
  "databaseType": "MySQL",
  "tableName": "orders",
  "tableSchema": "id, order_no, amount, create_time"
}
```

**cURL 命令:**
```bash
curl -X POST http://localhost:8080/api/ai/generate-sql \
  -H "Content-Type: application/json" \
  -d '{
    "requirement":"查询所有订单，按金额降序排列，只要最近7天的数据",
    "databaseType":"MySQL",
    "tableName":"orders",
    "tableSchema":"id, order_no, amount, create_time"
  }'
```

**期望响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sql": "SELECT id, order_no, amount, create_time\nFROM orders\nWHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)\nORDER BY amount DESC;",
    "databaseType": "MySQL"
  }
}
```

---

### 3. 作业配置提取测试

**接口:** `POST /api/ai/extract-job-config`

**请求示例:**
```
POST /api/ai/extract-job-config?description=从MySQL的user表同步数据到Hive的ods.user表，只同步最近一天的数据
```

**cURL 命令:**
```bash
curl -X POST "http://localhost:8080/api/ai/extract-job-config?description=从MySQL的user表同步数据到Hive的ods.user表，只同步最近一天的数据"
```

**期望响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": "{\"jobName\":\"MySQL用户数据同步\",\"sourceTable\":\"user\",\"targetTable\":\"ods.user\",\"filterCondition\":\"最近一天\",\"needsAggregation\":false,\"timeRange\":\"1 day\"}"
}
```

---

### 4. SQL 优化建议测试

**接口:** `POST /api/ai/optimize-sql`

**请求示例:**
```
POST /api/ai/optimize-sql?sql=SELECT * FROM orders WHERE status = '已完成'&dbType=MySQL
```

**cURL 命令:**
```bash
curl -X POST "http://localhost:8080/api/ai/optimize-sql?sql=SELECT%20*%20FROM%20orders%20WHERE%20status%20=%20'已完成'&dbType=MySQL"
```

**期望响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": "{\"issues\":[\"使用SELECT *会查询所有列，影响性能\",\"状态字段建议使用索引\"],\"suggestions\":[\"明确指定需要的列\",\"在status字段上创建索引\"],\"optimizedSql\":\"SELECT id, order_no, amount, create_time FROM orders WHERE status = '已完成'\",\"improvement\":\"预计提升30-50%\"}"
}
```

---

### 5. 智能问答测试

**接口:** `POST /api/ai/qa`

**请求示例:**
```
POST /api/ai/qa?question=如何优化Hive查询性能？
```

**cURL 命令:**
```bash
curl -X POST "http://localhost:8080/api/ai/qa?question=如何优化Hive查询性能？"
```

**期望响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "question": "如何优化Hive查询性能？",
    "answer": "Hive查询性能优化建议：\n1. 分区裁剪：合理使用分区字段\n2. 列裁剪：只查询需要的列\n3. 谓词下推：在WHERE子句中尽早过滤\n4. 使用ORC或Parquet格式\n5. 合理设置MapReduce参数\n6. 使用向量化查询\n7. 启用CBO优化器"
  }
}
```

---

## Swagger UI 测试

访问 Swagger 文档，可以直接在浏览器中测试所有接口：

```
http://localhost:8080/swagger-ui.html
```

在 Swagger UI 中：
1. 找到 **AI助手** 分组
2. 点击任意接口展开
3. 点击 **Try it out**
4. 输入参数
5. 点击 **Execute**
6. 查看响应结果

---

## 常见问题

### 1. 提示 API Key 未配置

**错误信息:**
```
API Key not configured
```

**解决方案:**
检查环境变量是否正确设置：
```bash
echo $DEEPSEEK_API_KEY
echo $ALI_AI_KEY
```

### 2. 连接超时

**错误信息:**
```
Connection timeout
```

**解决方案:**
- 检查网络连接
- 确认 API Key 有效
- 检查防火墙设置

### 3. AI 服务响应慢

**原因:**
- 网络延迟
- 模型处理时间
- 并发请求过多

**解决方案:**
- 优化提示词长度
- 使用更快的模型
- 增加超时时间

---

## 下一步

测试通过后，可以：

1. **集成到现有业务流程**
   - 在作业创建页面添加 AI 辅助按钮
   - 在 SQL 编辑器添加智能生成功能

2. **添加更多功能**
   - 对话历史管理
   - RAG 检索增强
   - 多轮对话上下文

3. **性能优化**
   - 添加缓存机制
   - 批量请求处理
   - 异步处理

4. **监控和日志**
   - 添加调用统计
   - 性能监控
   - 成本追踪

---

## 测试检查清单

- [ ] 环境变量已设置
- [ ] 应用启动成功
- [ ] 健康检查通过
- [ ] 通用对话功能正常
- [ ] SQL 生成功能正常
- [ ] 配置提取功能正常
- [ ] SQL 优化功能正常
- [ ] 智能问答功能正常
- [ ] Swagger 文档可访问
- [ ] 所有接口响应正常

**全部完成？恭喜！AI 功能已成功集成！** 🎉
