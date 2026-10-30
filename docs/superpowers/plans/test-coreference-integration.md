# 指代消解集成测试脚本

## 环境准备
1. 启动后端：`java -jar target/data-platform-1.0.0.jar`
2. 确认 Redis 运行中
3. 确认 DeepSeek API Key 已配置

## 测试用例

### 用例1：指代消解（作业ID）
**步骤**：
1. 发起对话：POST `/api/ai/chat/stream?message=查询作业ID为3的作业&userId=test1`
2. 等待响应
3. 同一会话：POST `/api/ai/chat/stream?message=刚那个作业再查一遍&userId=test1&conversationId={上一步返回的conversationId}`

**预期**：
- 日志显示：`指代消解：原始='刚那个作业再查一遍', 改写='查询作业ID为3的详情'`（或语义等价）
- AI 返回作业3的详情

### 用例2：原样透传（无指代）
**步骤**：
1. 新会话：POST `/api/ai/chat/stream?message=查询作业ID为7的详情&userId=test2`

**预期**：
- 日志显示：改写结果与原始消息相同或语义等价
- AI 返回作业7的详情

### 用例3：多轮指代
**步骤**：
1. POST `/api/ai/chat/stream?message=查询数据源ID为5的详情&userId=test3`
2. POST `/api/ai/chat/stream?message=这个数据源测试连接&conversationId={同上}`
3. POST `/api/ai/chat/stream?message=再测试一遍&conversationId={同上}`

**预期**：
- 第2轮：改写包含"数据源"和"5"
- 第3轮：改写包含"数据源"和"5"
