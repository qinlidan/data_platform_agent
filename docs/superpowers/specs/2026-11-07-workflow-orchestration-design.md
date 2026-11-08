# Workflow 编排架构设计

**日期**: 2026-11-07  
**类型**: 生产级架构升级  
**状态**: 待评审

## 目标与动机

### 为什么现在做

现有客服系统是直线型流程：用户输入 → 指代消解 → Agent（带工具）→ 流式输出。所有请求都进 Agent，无论是知识问答、业务操作还是闲聊，导致：

1. **成本浪费**：闲聊、简单问答也要调用 Agent + 工具，白烧 token
2. **响应质量不稳定**：知识类问题没有强制检索，Agent 可能编造答案
3. **高危操作无保护**：创建+提交+发布这种复合操作，Agent 可能一次性执行完，缺少人工卡点

需要在 Agent 之前加一层**确定性编排**：根据意图分流，简单问题走轻量路径，危险操作走确定性流程。

### 成功的样子

1. 问 "Spark 参数怎么调优"，日志显示：意图识别 → QA → 强制 RAG 检索 → 置信度闸通过 → Agent 生成
2. 问 "查询作业 ID 为 3"，日志显示：意图识别 → DATAPLATFORM_MANAGER/OTHER → 直接进 Agent（无检索）
3. 说 "你好"，秒返固定话术，不调用 Agent
4. 说 "帮我创建、提交并发布作业 X"，系统识别为高危操作，串行执行三步，最后发布时弹确认框

## 非目标（本次不做）

- 意图识别模型的精调或分类器训练（用 prompt 工程凑合，下一章再优化）
- 上下文管理策略升级（滑动窗口、压缩、分层记忆等）
- MCP 接入
- 多轮澄清（意图不明时追问用户）

## 反目标（什么算失败）

- 引入外部 Workflow 引擎（Camunda/Temporal），增加运维成本
- 破坏现有流式输出体验（用户看到回答变慢或卡顿）
- 意图识别准确率低于 80%（通过人工标注样例验证）

## 约束

- **技术栈不可变**：Spring AI 1.0.0、Spring Boot 3.2.2、MyBatis-Plus、PostgreSQL
- **已有组件必须复用**：CoreferenceResolutionService、KnowledgeRetrieval、现有 Tool 实现
- **流式输出不可降级**：所有路径都必须保持 SSE 流式返回

## 架构设计

### 整体流程

```
用户输入
  ↓
【节点1】指代消解（复用 CoreferenceResolutionService）
  ↓
【节点2】意图识别（新增 IntentClassificationService）
  ↓ 输出：{domain, intent_type}
  ↓
【节点3】WorkflowOrchestrationService 分流
  ├─ domain=QA
  │    ↓
  │  【节点4】RAG 检索（复用 KnowledgeRetrieval.retrieve）
  │    ↓ 返回 Result{refused, prompt, references}
  │    ↓
  │  【节点5】置信度闸判断
  │    ├─ refused=true → 返回固定话术 + 记录低置信问题（已在 KnowledgeRetrieval 内完成）
  │    └─ refused=false → 把 prompt（含参考资料）注入 user message → 进 Agent
  │
  ├─ domain=DATAPLATFORM_MANAGER && intent_type=CREATE_SUBMIT_PUBLISH
  │    ↓
  │  【节点6】确定性流程（新增 DeterministicWorkflowService）
  │    ├─ 创建作业（调用 JobService）
  │    ├─ 提交作业（调用 JobService）
  │    └─ 发布作业（人工确认 + 调用 JobService）
  │
  ├─ domain=DATAPLATFORM_MANAGER && intent_type=OTHER
  │    ↓
  │  直接进 Agent（自主调用工具）
  │
  ├─ domain=CHITCHAT
  │    ↓
  │  返回固定话术："很高兴和你聊天！有什么数据平台相关的问题可以问我。"
  │
  └─ domain=OTHER
       ↓
     返回固定话术："抱歉，我只能回答数据平台相关的问题。"
```

### 核心组件

#### 1. IntentClassificationService（意图识别）

**输入**：改写后的用户消息  
**输出**：`IntentClassificationResult { domain, intentType }`

```java
public enum Domain {
    DATAPLATFORM_MANAGER,  // 平台操作（作业管理、数据源管理）
    QA,                    // 问答（Spark调优、平台使用咨询）
    CHITCHAT,              // 闲聊
    OTHER                  // 其他
}

public enum IntentType {
    CREATE_SUBMIT_PUBLISH,  // 高危复合操作：创建+提交+发布
    OTHER,                  // 其他操作（单步或简单复合）
    NONE                    // 非平台操作域不需要二级分类
}
```

**实现方式**：
- 使用 Spring AI ChatClient 调用模型，system prompt 强制输出 JSON
- prompt 示例：

```
你是意图分类助手。根据用户消息，输出 JSON 格式的分类结果。

【分类规则】
一级分类（domain）：
- DATAPLATFORM_MANAGER：涉及作业管理（查询、创建、修改、删除、发布作业）或数据源管理（查询、创建、修改、删除、测试连接）
- QA：询问 Spark 参数调优、平台使用方法、故障诊断、性能优化等知识问答
- CHITCHAT：打招呼、闲聊、问候
- OTHER：以上都不是

二级分类（intent_type）：
- 仅当 domain=DATAPLATFORM_MANAGER 时才分类：
  - CREATE_SUBMIT_PUBLISH：明确要求"创建、提交并发布"或"一键上线"作业的复合操作
  - OTHER：其他操作（查询、单独创建、单独发布、修改、删除等）
- 其他 domain 统一返回 NONE

【输出格式】
严格输出 JSON，不要输出任何其他内容：
{"domain": "...", "intent_type": "..."}

【示例】
用户: Spark 的 executor 内存怎么设置？
输出: {"domain": "QA", "intent_type": "NONE"}

用户: 查询作业 ID 为 3 的详情
输出: {"domain": "DATAPLATFORM_MANAGER", "intent_type": "OTHER"}

用户: 帮我创建、提交并发布作业 user_sync
输出: {"domain": "DATAPLATFORM_MANAGER", "intent_type": "CREATE_SUBMIT_PUBLISH"}

用户: 你好
输出: {"domain": "CHITCHAT", "intent_type": "NONE"}

用户: 今天天气怎么样
输出: {"domain": "OTHER", "intent_type": "NONE"}
```

- 手动解析 JSON（使用 Jackson ObjectMapper）
- 解析失败或格式非法时降级为 domain=OTHER, intentType=NONE

#### 2. WorkflowOrchestrationService（编排服务）

**职责**：根据意图分类结果，调度不同的处理路径。

**接口**：
```java
public Flux<ServerSentEvent<Map<String, Object>>> orchestrate(
    String resolvedMessage,     // 改写后的消息
    String originalMessage,     // 原始消息（用于落库）
    Long conversationId,
    String userId,
    String confirmationToken
)
```

**分流逻辑**（伪代码）：
```java
IntentClassificationResult intent = intentClassificationService.classify(resolvedMessage);

return switch (intent.domain()) {
    case QA -> handleQA(resolvedMessage, conversationId, userId);
    case DATAPLATFORM_MANAGER -> {
        if (intent.intentType() == IntentType.CREATE_SUBMIT_PUBLISH) {
            yield handleDeterministicWorkflow(resolvedMessage, conversationId, userId, confirmationToken);
        } else {
            yield handleAgentWithTools(resolvedMessage, conversationId, userId, confirmationToken);
        }
    }
    case CHITCHAT -> returnFixedResponse("很高兴和你聊天！有什么数据平台相关的问题可以问我。");
    case OTHER -> returnFixedResponse("抱歉，我只能回答数据平台相关的问题。");
};
```

#### 3. QA 类处理（handleQA）

**流程**：
```java
// 1. 调用 KnowledgeRetrieval.retrieve（复用现有逻辑）
KnowledgeRetrieval.Result result = knowledgeRetrieval.retrieve(
    resolvedMessage, 
    conversationId, 
    0.5,  // vectorSimThreshold（建议值，可配置）
    0.6   // rerankThreshold（你要求的精排阈值）
);

// 2. 置信度闸判断
if (result.refused()) {
    // 拒答：返回固定话术（低置信问题已在 retrieve 内记录）
    return returnFixedResponse(result.refusalMessage());
}

// 3. 通过：注入检索结果到 user message，调用 Agent
String userMessageWithContext = result.prompt(); // 已经是 PromptBuilder 格式化好的，包含参考资料 + 问题
return callAgentStream(userMessageWithContext, conversationId, userId, null, result.references());
```

**callAgentStream**：
- 构建 ChatClient（复用现有 Agent 的工具配置）
- **不传入检索工具**（FaqTool），避免 Agent 二次检索
- 只传入 JobTool、DataSourceTool
- 流式返回（Flux<ServerSentEvent>）
- 调用完成后保存引用：`knowledgeRetrieval.saveCitations(conversationId, references)`

#### 4. 确定性流程（handleDeterministicWorkflow）

**触发条件**：domain=DATAPLATFORM_MANAGER && intentType=CREATE_SUBMIT_PUBLISH

**流程**：
```
1. 调用 LLM 提取参数（作业名称、数据源、表名等）
   ↓
2. 参数不全 → 返回 "缺少必要参数：xxx，请补充"
   ↓
3. 创建作业（JobService.createJob）
   ↓ 失败 → 返回错误，终止流程
   ↓
4. 提交作业（JobService.submitJob 或类似方法）
   ↓ 失败 → 返回错误，保留已创建的作业（不回滚）
   ↓
5. 发布作业 - 需人工确认
   ├─ 无 confirmationToken → 返回 SSE 事件 {event: "need_confirmation", data: {action: "publish", jobId: xxx}}
   └─ 有 confirmationToken → 验证 token，调用 JobService.publishJob
```

**参数提取 prompt**：
```
你是参数提取助手。从用户消息中提取创建作业所需的参数。

【必填参数】
- jobCode: 作业编码（如用户未提供，生成格式：job_表名_随机数）
- jobName: 作业名称（如用户未提供，默认用表名）
- jobType: 作业类型（DB2HIVE、HIVE2HIVE 等）
- sourceDatasourceCode: 源数据源编码
- sourceDatabase: 源数据库名
- sourceTable: 源表名
- targetDatabase: 目标数据库名
- engineType: 引擎类型（HIVE、SPARK 等）

【输出格式】
严格输出 JSON，缺少的参数用 null：
{
  "jobCode": "...",
  "jobName": "...",
  "jobType": "...",
  "sourceDatasourceCode": "...",
  "sourceDatabase": "...",
  "sourceTable": "...",
  "targetDatabase": "...",
  "engineType": "...",
  "missingParams": ["参数1", "参数2"]  // 缺少的必填参数列表
}

【用户消息】
{用户消息}
```

**流式返回规范**：
- 每一步成功后发送 SSE 事件：`{event: "step_complete", data: {step: "create", message: "作业创建成功"}}`
- 发布需确认时：`{event: "need_confirmation", data: {action: "publish", jobId: xxx, jobCode: "xxx"}}`
- 全部完成：`{event: "workflow_complete", data: {message: "作业已成功创建、提交并发布"}}`

#### 5. Agent 自主决策（handleAgentWithTools）

**流程**：
- 直接调用现有 Agent 逻辑（复用 AiAssistantController.chatStream 的核心代码）
- 传入所有工具：JobTool、DataSourceTool、FaqTool
- 流式返回

### 前后端交互

#### 前端改动

1. **意图显示（可选）**：在聊天界面显示识别到的意图（调试用）
2. **确认按钮**：监听 `need_confirmation` 事件，弹出确认框，用户点击后携带 `X-Confirmation-Token` 重新请求
3. **步骤进度**：监听 `step_complete` 事件，显示"正在创建作业... ✓"、"正在提交作业... ✓"

#### 后端接口

**不新增接口**，复用现有：
- `GET /api/ai/conversations/{conversationId}/chat/stream`

**新增内部服务**：
- `IntentClassificationService`
- `WorkflowOrchestrationService`
- `DeterministicWorkflowService`

**修改点**：
- `AiAssistantController.chatStream`：在指代消解后，调用 `WorkflowOrchestrationService.orchestrate`，而不是直接进 Agent

### 配置

**application.yml 新增**：
```yaml
workflow:
  intent-classification:
    enabled: true
    # 意图识别降级开关：关闭时所有请求直接进 Agent（保持现有行为）
  
  rag:
    vector-sim-threshold: 0.5   # 向量相似度阈值
    rerank-threshold: 0.6       # 精排阈值（你要求的）
  
  deterministic:
    require-confirmation: true  # 高危操作是否需要确认
```

## 实现策略

### 开发顺序

**Phase 1：意图识别**
1. 实现 `IntentClassificationService`（prompt + JSON 解析）
2. 单元测试：准备 20 条标注样例，验证准确率 ≥ 80%

**Phase 2：编排服务骨架**
1. 实现 `WorkflowOrchestrationService`（空壳，只打日志）
2. 集成到 `AiAssistantController.chatStream`
3. 手动测试：每条路径都能进入正确分支

**Phase 3：QA 路径**
1. 实现 `handleQA`（调用 KnowledgeRetrieval + 注入 Agent）
2. 集成测试：问 "Spark 参数调优"，验证强制检索 + 置信度闸

**Phase 4：确定性流程**
1. 实现 `DeterministicWorkflowService`（参数提取 + 三步串行）
2. 实现确认机制（token 生成 + 验证）
3. 集成测试：创建+提交+发布流程

**Phase 5：闲聊和兜底**
1. 实现固定话术返回
2. 验收测试：三个场景（Spark 问题、查询作业、闲聊）

### 测试策略

**意图识别测试集**（20 条，覆盖 4 类）：
- QA：5 条（Spark 调优、性能优化、故障诊断、平台使用咨询、知识库相关）
- DATAPLATFORM_MANAGER/CREATE_SUBMIT_PUBLISH：3 条
- DATAPLATFORM_MANAGER/OTHER：7 条（查询、创建、修改、删除、发布）
- CHITCHAT：2 条
- OTHER：3 条

**集成测试场景**：
1. QA 类 + 置信度闸通过：返回 Agent 答案 + 引用
2. QA 类 + 置信度闸拒绝：返回固定话术 + 记录低置信问题
3. 高危操作：三步串行，最后确认
4. 普通操作：Agent 自主调用工具
5. 闲聊：秒返固定话术

### 风险与降级

**风险 1：意图识别不准**
- 降级方案：配置开关关闭意图识别，所有请求直接进 Agent（保持现有行为）

**风险 2：置信度闸过严**
- 降级方案：调低 rerankThreshold（从 0.6 降到 0.5）

**风险 3：确定性流程中途失败**
- 策略：不回滚，保留中间状态（已创建的作业保留），返回明确错误信息

## 验收标准

1. **Spark 参数调优问题**：日志显示 `IntentClassificationService → QA → KnowledgeRetrieval → 置信度闸通过 → Agent 生成`
2. **查询作业**：日志显示 `IntentClassificationService → DATAPLATFORM_MANAGER/OTHER → Agent 自主调用工具`
3. **闲聊**：秒返固定话术，日志无 Agent 调用
4. **创建+提交+发布**：日志显示三步串行，发布前弹确认框
5. **意图识别准确率**：20 条测试集 ≥ 80%（16/20）

## 实现者决策

以下由实现者（builder）决定，需在实现中明确：

1. **ConfirmationToken 的生成和验证方式**：JWT？随机 UUID + Redis 存储？
2. **参数提取失败的重试策略**：立即返回缺参提示，还是追问一次？
3. **日志埋点格式**：统一用 JSON 结构化日志还是自然语言？
4. **异常处理细节**：意图识别超时、参数提取超时的 fallback 行为
5. **监控指标**：是否需要 Prometheus metrics（各路径的 QPS、延迟、错误率）

## 我的决策（非用户要求）

1. **配置项前缀**：统一用 `workflow.*`（而非 `ai.*` 或 `orchestration.*`），与设计文档标题对齐
2. **IntentClassificationResult 定义为 record**：简洁且不可变
3. **降级优先级**：意图识别失败 → domain=OTHER（而非直接进 Agent），保持保守策略
4. **QA 路径不传 FaqTool**：避免 Agent 在已有检索结果的情况下二次检索，造成混乱
5. **固定话术可配置**：放在 application.yml 的 `workflow.responses.*`，方便运营调整文案

## 附录：现有组件接口

### CoreferenceResolutionService
```java
public String resolveAndRewrite(String userMessage, String conversationId)
```

### KnowledgeRetrieval
```java
public record Result(boolean refused, String refusalMessage, String prompt,
                     List<RerankResult> references, Double topRelevance)

public Result retrieve(String query, Long conversationId, 
                      double vectorSimThreshold, double rerankThreshold)

public void saveCitations(Long conversationId, List<RerankResult> reranked)

public void recordLowConfidence(Long conversationId, String question, 
                               String source, String reason)
```

### PromptBuilder
```java
public String buildPromptWithSearchResults(String query, List<RerankResult> results)
```
- 输出格式：`[参考资料 1]\n{文本}\n来源: {路径}\n\n[参考资料 2]\n...用户问题: {query}\n请基于以上参考资料回答...`

### 现有工具
- `JobTool`：作业管理（查询、创建、修改、删除、发布）
- `DataSourceTool`：数据源管理
- `FaqTool`：内部调用 KnowledgeRetrieval（与 QA 路径逻辑重复，新架构中 QA 路径不再使用）
