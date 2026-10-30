# 指代消解集成测试脚本

## 前置条件
- 启动应用：`mvn spring-boot:run`
- 确认 Redis 运行中
- 确认 DeepSeek API Key 已配置

## 前端集成要求

### conversationId 生命周期管理
后端通过 SSE 事件返回 `conversationId`：
```javascript
// 首次对话：不传 conversationId
fetch('/api/ai/chat/stream?message=查询作业ID为3的作业&userId=testUser')

// 监听 SSE 事件
eventSource.addEventListener('conversation_id', (e) => {
  const data = JSON.parse(e.data);
  const conversationId = data.conversationId;
  
  // 保存到 localStorage（7天过期）
  const expiresAt = Date.now() + 7 * 24 * 60 * 60 * 1000;
  localStorage.setItem('conversationId', conversationId);
  localStorage.setItem('conversationIdExpires', expiresAt);
});

// 后续对话：从 localStorage 读取并传递
function getConversationId() {
  const id = localStorage.getItem('conversationId');
  const expires = localStorage.getItem('conversationIdExpires');
  if (!id || !expires || Date.now() > parseInt(expires)) {
    // 过期或不存在，清除
    localStorage.removeItem('conversationId');
    localStorage.removeItem('conversationIdExpires');
    return null;
  }
  return id;
}

// 发送请求时携带
const conversationId = getConversationId();
const url = conversationId 
  ? `/api/ai/chat/stream?message=${msg}&userId=${userId}&conversationId=${conversationId}`
  : `/api/ai/chat/stream?message=${msg}&userId=${userId}`;
fetch(url);
```

### conversationId 与 ChatMemory key 对应关系
- 后端使用 `String.valueOf(conversationId)` 作为 ChatMemory key（纯数字）
- 前端传递时直接传数字，不要加前缀

---

## 测试用例

### 用例1：指代消解（作业ID）
**步骤**：
1. 首次对话（不传 conversationId）：
   ```bash
   curl -N "http://localhost:8080/api/ai/chat/stream?message=查询作业ID为3的作业&userId=testUser"
   ```
   观察返回的 SSE 事件 `conversation_id`，提取 `conversationId`（假设为 `123`）

2. 第二轮对话（传 conversationId）：
   ```bash
   curl -N "http://localhost:8080/api/ai/chat/stream?message=刚那个作业再查一遍&userId=testUser&conversationId=123"
   ```

**预期**：
- 日志输出：`指代消解: conversationId=123, 原始='刚那个作业再查一遍', 改写='查询作业ID为3的详情'`
- 返回作业ID为3的详情

---

### 用例2：原样透传（无指代）
**步骤**：
```bash
curl -N "http://localhost:8080/api/ai/chat/stream?message=查询作业ID为5的详情&userId=testUser"
```

**预期**：
- 日志输出：`指代消解: conversationId={新ID}, 原始='查询作业ID为5的详情', 改写='查询作业ID为5的详情'`
- AI 返回作业5的详情

---

### 用例3：多轮指代
**步骤**：
1. 首次对话：
   ```bash
   curl -N "http://localhost:8080/api/ai/chat/stream?message=查询数据源ID为5的详情&userId=testUser"
   ```
   提取 `conversationId`（假设为 `456`）

2. 第二轮：
   ```bash
   curl -N "http://localhost:8080/api/ai/chat/stream?message=这个数据源测试连接&userId=testUser&conversationId=456"
   ```

3. 第三轮：
   ```bash
   curl -N "http://localhost:8080/api/ai/chat/stream?message=再测试一遍&userId=testUser&conversationId=456"
   ```

**预期**：
- 第2轮改写包含"数据源ID为5"
- 第3轮改写包含"数据源ID为5"+"测试连接"

---

## 验收标准
- [ ] 所有用例日志输出符合预期
- [ ] 指代消解不污染主对话历史（查看 Redis `chatMemory:{conversationId}` 只有用户原始消息）
- [ ] 前端能正确接收并保存 `conversationId`
- [ ] 7天后 localStorage 过期，新对话创建新 conversationId
