# StateGraph AI Agent 架构说明

## 📋 概述

基于 **StateGraph** 的 Agent 编排系统，实现了：
- 🤖 **Agent 推理**：使用 **Spring AI** 原生 ChatModel 进行意图识别和工具调用
- 🔧 **工具执行**：支持作业管理、数据源管理、SQL执行等
- ✅ **人工审批**：敏感操作（删除、发布）需要用户确认
- 💾 **状态持久化**：基于 Redis Checkpoint 实现状态恢复

## 🏆 技术栈

- **Spring AI**：原生 Message、ToolCall、FunctionCallback
- **Spring AI Alibaba**：Graph 编排能力（兼容）
- **Spring Boot WebFlux**：响应式流式输出
- **Redis**：状态持久化和审批 Checkpoint
- **大模型**：支持 OpenAI / 通义千问 / 其他兼容模型

---

## 🏗️ 核心架构

### 1. StateGraph 流程图

```
START
  ↓
AgentNode (推理)
  ↓
条件判断:
  ├─ 需要审批 → INTERRUPT → 保存 Checkpoint → 等待用户审批 → ToolNode
  ├─ 有工具调用 → ToolNode → AgentNode (继续推理)
  └─ 否则 → END
```

### 2. 核心组件

| 组件 | 职责 |
|------|------|
| **AgentState** | 状态对象，使用 **Spring AI 原生类型**：<br>- `List<Message>` 消息历史<br>- `List<AssistantMessage.ToolCall>` 工具调用<br>- 审批队列、元数据、控制标志 |
| **GraphNode** | 节点接口，定义 `execute(state)` 方法 |
| **AgentNode** | 推理节点，调用 **Spring AI ChatModel**，识别工具调用 |
| **ToolNode** | 工具执行节点，执行工具并返回 **ToolResponseMessage** |
| **StateGraph** | 图编排核心，控制流程跳转 |
| **ToolExecutor** | 工具管理器，注册和执行工具 |
| **RedisCheckpoint** | 状态持久化，支持审批流程 |
| **ApprovalService** | 人工审批服务，处理用户确认 |

---

## 🔧 工具清单

### 作业管理
- `createJob` - 创建作业
- `updateJob` - 更新作业
- `deleteJob` - 删除作业 ⚠️ **需要审批**
- `publishJob` - 发布作业 ⚠️ **需要审批**
- `getJobDetail` - 查询作业详情
- `searchJobs` - 搜索作业

### 数据源管理
- `createDataSource` - 创建数据源
- `updateDataSource` - 更新数据源
- `deleteDataSource` - 删除数据源 ⚠️ **需要审批**
- `getDataSourceDetail` - 查询数据源详情
- `listDataSources` - 列出所有数据源

### 查询工具
- `executeSQL` - 执行 SQL ⚠️ **需要审批**
- `testConnection` - 测试连接

---

## 🚀 快速开始

### 1. 启动项目

```bash
# 确保 Redis 已启动
redis-server

# 启动 Spring Boot
mvn spring-boot:run
```

### 2. 访问测试页面

打开浏览器：http://localhost:8080/stategraph-test.html

### 3. 测试示例

#### ✅ 普通查询（无需审批）
```
用户：查询所有作业
AI：[调用 searchJobs] 找到 3 个作业：job_001, job_002, job_003
```

#### ⚠️ 删除操作（需要审批）
```
用户：删除作业 job_001
AI：[检测到敏感操作，触发 INTERRUPT]
前端：显示审批卡片
用户：点击"确认执行"
AI：[恢复执行，调用 deleteJob] 作业删除成功
```

---

## 📡 API 接口

### 1. 流式对话

**GET** `/api/ai/v2/chat/stream`

**参数：**
- `message` - 用户消息
- `userId` - 用户ID（可选）

**SSE 事件：**
- `text` - 文本增量
- `approval_required` - 需要审批
- `done` - 结束
- `error` - 错误

**示例：**
```javascript
const eventSource = new EventSource('/api/ai/v2/chat/stream?message=查询作业');

eventSource.addEventListener('text', (e) => {
    const data = JSON.parse(e.data);
    console.log(data.content);
});

eventSource.addEventListener('approval_required', (e) => {
    const data = JSON.parse(e.data);
    console.log('需要审批:', data.checkpointId);
});
```

### 2. 处理审批

**POST** `/api/ai/v2/approval`

**Body：**
```json
{
  "checkpointId": "1714123456789_123",
  "approved": true
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "message": "作业删除成功"
  }
}
```

---

## 🔍 工作流详解

### 场景1：普通查询

```
用户：查询所有作业

1. AgentNode 推理 → 识别需要调用 searchJobs
2. ToolNode 执行 → 返回作业列表
3. AgentNode 推理 → 生成自然语言响应
4. END
```

### 场景2：删除操作（需审批）

```
用户：删除作业 job_001

1. AgentNode 推理 → 识别需要调用 deleteJob
2. AgentNode 检测 → deleteJob 需要审批，设置 needsInterrupt=true
3. StateGraph → 跳转到 INTERRUPT
4. RedisCheckpoint → 保存状态，返回 checkpointId
5. 前端 → 显示审批卡片
6. 用户 → 点击"确认执行"
7. ApprovalService → 恢复状态，清除 needsInterrupt
8. ToolNode 执行 → 调用 deleteJob
9. AgentNode 推理 → 生成响应
10. END
```

---

## 🔐 审批机制

### 1. 触发条件

工具定义中的 `requiresApproval` 为 `true`：

```java
OpenAIService.ToolDefinition.builder()
    .name("deleteJob")
    .description("删除作业")
    .requiresApproval(true) // 标记为需要审批
    .parameters(...)
    .build();
```

### 2. 审批流程

```
AgentNode 检测到工具需要审批
  ↓
将工具调用加入 pendingApprovals
  ↓
设置 needsInterrupt = true
  ↓
StateGraph 跳转到 INTERRUPT
  ↓
RedisCheckpoint 保存状态
  ↓
返回 checkpointId 给前端
  ↓
用户点击"确认"或"取消"
  ↓
ApprovalService 恢复状态
  ↓
ToolNode 执行工具
```

### 3. Checkpoint 数据结构

```json
{
  "messages": [
    {"role": "user", "content": "删除作业 job_001"},
    {"role": "assistant", "content": "好的，正在删除..."}
  ],
  "toolCalls": [
    {
      "name": "deleteJob",
      "params": {"jobCode": "job_001"}
    }
  ],
  "pendingApprovals": [
    {
      "toolName": "deleteJob",
      "params": {"jobCode": "job_001"},
      "reason": "删除操作不可逆"
    }
  ],
  "needsInterrupt": true,
  "finalResponse": null
}
```

---

## 📦 项目结构

```
src/main/java/com/dataplatform/
├── ai/
│   ├── graph/
│   │   ├── AgentState.java          # 状态对象
│   │   ├── GraphNode.java           # 节点接口
│   │   ├── AgentNode.java           # 推理节点
│   │   ├── ToolNode.java            # 工具执行节点
│   │   ├── ToolExecutor.java        # 工具管理器
│   │   ├── ToolFunction.java        # 工具函数接口
│   │   └── StateGraph.java          # 图编排核心
│   ├── checkpoint/
│   │   └── RedisCheckpoint.java     # 状态持久化
│   ├── approval/
│   │   └── ApprovalService.java     # 人工审批服务
│   └── config/
│       └── ToolRegistry.java        # 工具注册器
├── controller/
│   └── StateGraphController.java    # API 控制器
└── service/
    └── OpenAIService.java           # OpenAI 调用封装
```

---

## 🛠️ 扩展指南

### 1. 添加新工具

在 `ToolRegistry.java` 中注册：

```java
toolExecutor.registerTool("myTool", params -> {
    // 工具逻辑
    return Map.of("status", "success");
});
```

在 `AgentNode.java` 中定义工具描述：

```java
OpenAIService.ToolDefinition.builder()
    .name("myTool")
    .description("我的工具")
    .requiresApproval(false) // 是否需要审批
    .parameters(Map.of(
        "param1", Map.of("type", "string", "description", "参数1")
    ))
    .build();
```

### 2. 自定义审批逻辑

修改 `AgentNode.java` 中的审批检测逻辑：

```java
private boolean needsApproval(String toolName, Map<String, Object> params) {
    // 自定义逻辑
    if ("updateJob".equals(toolName)) {
        String status = (String) params.get("status");
        return "RUNNING".equals(status); // 只有启动作业需要审批
    }
    return false;
}
```

### 3. 对接真实服务

修改 `ToolRegistry.java` 中的工具实现：

```java
@Autowired
private JobService jobService;

private ToolFunction deleteJobTool() {
    return params -> {
        String jobCode = (String) params.get("jobCode");
        jobService.deleteJob(jobCode); // 调用真实服务
        return Map.of("status", "success");
    };
}
```

---

## ⚠️ 注意事项

1. **Redis 配置**：确保 Redis 已启动，Checkpoint TTL 默认 10 分钟
2. **OpenAI API**：需要配置 `openai.api.key` 和 `openai.api.url`
3. **工具定义**：工具名称必须唯一，参数格式必须符合 JSON Schema
4. **审批超时**：Checkpoint 10 分钟后自动过期
5. **并发控制**：当前实现为单线程，生产环境建议加分布式锁

---

## 🧪 测试建议

### 1. 单元测试

```java
@Test
void testAgentNode() {
    AgentState state = new AgentState();
    state.addMessage("user", "查询作业");
    
    AgentState result = agentNode.execute(state);
    
    assertFalse(result.getToolCalls().isEmpty());
}
```

### 2. 集成测试

```java
@Test
void testStateGraphWithApproval() {
    AgentState state = new AgentState();
    state.addMessage("user", "删除作业 job_001");
    
    Flux<AgentState> flux = stateGraph.stream(state);
    
    StepVerifier.create(flux)
        .assertNext(s -> assertTrue(s.isNeedsInterrupt()))
        .verifyComplete();
}
```

### 3. 前端测试

访问 http://localhost:8080/stategraph-test.html，依次测试：
- ✅ 查询操作（无审批）
- ✅ 删除操作（有审批）
- ✅ 取消审批
- ✅ 确认审批

---

## 📚 参考资料

- [LangGraph Documentation](https://langchain-ai.github.io/langgraph/)
- [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
- [Spring WebFlux SSE](https://docs.spring.io/spring-framework/reference/web/webflux/webflux-sse.html)

---

## 🎯 后续优化

- [ ] 支持多轮对话（会话管理）
- [ ] 支持流式工具调用（边推理边执行）
- [ ] 支持并行工具调用（一次调用多个工具）
- [ ] 支持工具调用链（工具A的输出作为工具B的输入）
- [ ] 支持审批超时（自动拒绝）
- [ ] 支持审批历史记录
- [ ] 支持自定义审批流程（多级审批）
- [ ] 支持工具调用监控（成功率、耗时）
