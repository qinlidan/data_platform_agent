# 异步落表功能测试指南

## 启动服务

```bash
java -jar target/data-platform-1.0.0.jar
```

或使用 Maven：
```bash
mvn spring-boot:run
```

## 测试场景

### 1. 前端页面测试（推荐）

访问：http://localhost:8080/ai-chat.html

**测试步骤：**
1. 页面自动创建会话
2. 输入消息："列出所有数据源"
3. 观察：
   - 实时流式响应
   - 工具调用过程（黄色背景）
   - 打字动画效果

**验证点：**
- 打开数据库，查看 `t_conversation` 和 `t_message` 表
- 应该看到：
  - 1条会话记录
  - 3条消息记录（用户消息 + 助手消息 + 工具调用）

---

### 2. API 测试（curl）

#### 2.1 创建会话
```bash
curl -X POST http://localhost:8080/api/conversations \
  -H "Content-Type: application/json" \
  -d '{"userId": "test_user", "title": "测试会话"}'
```

响应：
```json
{
  "code": 200,
  "data": {
    "conversationId": 1851234567890,
    "title": "测试会话"
  }
}
```

#### 2.2 流式对话（带会话ID）
```bash
curl -N "http://localhost:8080/api/ai/chat/stream?message=列出所有作业&conversationId=1851234567890"
```

观察：
- SSE 事件流输出
- `event: text` - 助手回复
- `event: tool_start` - 工具开始
- `event: tool_end` - 工具结果
- `event: done` - 完成

#### 2.3 查看会话消息历史
```bash
curl http://localhost:8080/api/conversations/1851234567890/messages
```

响应：
```json
{
  "code": 200,
  "data": [
    {
      "id": 1851234567891,
      "conversationId": 1851234567890,
      "role": "user",
      "content": "列出所有作业",
      "createdAt": "2024-03-20T10:30:00"
    },
    {
      "id": 1851234567892,
      "conversationId": 1851234567890,
      "role": "assistant",
      "content": "以下是所有作业：...",
      "createdAt": "2024-03-20T10:30:05"
    },
    {
      "id": 1851234567893,
      "conversationId": 1851234567890,
      "role": "tool",
      "content": "[工具] listJobs\n...",
      "createdAt": "2024-03-20T10:30:04"
    }
  ]
}
```

#### 2.4 查看用户会话列表
```bash
curl "http://localhost:8080/api/conversations?userId=test_user"
```

#### 2.5 更新会话标题
```bash
curl -X PATCH http://localhost:8080/api/conversations/1851234567890/title \
  -H "Content-Type: application/json" \
  -d '{"title": "作业管理对话"}'
```

#### 2.6 软删除会话
```bash
curl -X DELETE http://localhost:8080/api/conversations/1851234567890
```

---

### 3. 性能测试

#### 3.1 并发测试
使用 `ab`（Apache Bench）测试：
```bash
ab -n 100 -c 10 -p message.json -T "application/json" \
  http://localhost:8080/api/ai/chat
```

`message.json`:
```json
{"message": "你好"}
```

**验证点：**
- 响应时间不因落表操作增加
- 数据库中有 200 条消息（100 次请求 × 2 条消息）

#### 3.2 流式响应延迟测试
```bash
time curl -N "http://localhost:8080/api/ai/chat/stream?message=列出所有数据源"
```

**验证点：**
- 首字节时间（TTFB）< 200ms
- 不因数据库写入阻塞

---

### 4. 异常场景测试

#### 4.1 数据库连接失败
1. 停止数据库服务
2. 发送对话请求
3. **预期结果**：AI 仍正常响应，但后台日志显示落表失败

#### 4.2 线程池满载
1. 调整 `AsyncConfig` 中的 `corePoolSize=1, queueCapacity=1`
2. 快速发送多个请求
3. **预期结果**：触发 `CallerRunsPolicy`，在主线程执行落表

---

## 日志观察

### 正常流程日志示例
```
[msg-save-1] MessageEventListener : [MessageEventListener] Saving message: conversationId=1851234567890, role=user
[msg-save-2] MessageEventListener : [MessageEventListener] Saving message: conversationId=1851234567890, role=assistant
[msg-save-3] MessageEventListener : [MessageEventListener] Saving message: conversationId=1851234567890, role=tool
```

### 关键观察点
1. **线程名前缀**：`msg-save-*` 表示异步执行
2. **执行顺序**：用户消息 → 工具调用 → 助手消息
3. **时间戳**：落表时间应晚于响应返回时间

---

## 数据库验证

### 查询会话
```sql
SELECT * FROM t_conversation ORDER BY created_at DESC LIMIT 10;
```

### 查询消息
```sql
SELECT 
  m.id, 
  m.conversation_id, 
  m.role, 
  LEFT(m.content, 50) AS content_preview,
  m.created_at
FROM t_message m
ORDER BY m.created_at DESC
LIMIT 20;
```

### 统计消息分布
```sql
SELECT 
  role, 
  COUNT(*) AS count
FROM t_message
GROUP BY role;
```

预期结果：
```
role      | count
----------|------
user      | 100
assistant | 100
tool      | 50
```

---

## 问题排查

### 1. 消息未保存
- 检查 `@EnableAsync` 是否启用
- 检查 `ApplicationEventPublisher` 是否注入
- 查看后台日志是否有异常

### 2. 消息顺序错乱
- 检查 `doOnComplete()` 位置
- 确认助手消息在流结束后才发事件

### 3. 性能下降
- 检查线程池配置
- 使用 JMX 监控线程池状态
- 检查数据库连接池是否耗尽

---

## 监控指标

### JMX Beans
- `java.lang:type=Threading` - 查看线程数
- `java.util.concurrent.ThreadPoolExecutor` - 查看线程池状态

### 关键指标
- 活跃线程数（Active Count）
- 队列大小（Queue Size）
- 完成任务数（Completed Task Count）

---

## 下一步

1. ✅ 异步落表基础功能
2. ✅ 会话管理 API
3. ✅ 前端演示页面
4. ⏳ 添加 Redis 缓存（可选）
5. ⏳ 添加消息审计日志（可选）
6. ⏳ 添加消息重试机制（可选）
