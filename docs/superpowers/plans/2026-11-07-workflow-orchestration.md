# Workflow 编排架构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有客服系统中实现确定性 Workflow 编排，根据用户意图分流到不同处理路径（RAG 检索、Agent 工具调用、确定性流程、固定话术），降低成本并提高响应质量。

**Architecture:** 
- 在 AiAssistantController.chatStream 的指代消解后，插入意图识别和编排服务
- WorkflowOrchestrationService 根据意图分类结果（domain + intentType）分流到 4 条路径
- QA 类强制走 RAG 检索 + 置信度闸，通过后注入检索结果到 Agent
- 高危操作走确定性三步流程（创建→提交→发布，最后确认）

**Tech Stack:** 
- Spring AI 1.0.0 (ChatClient, Flux streaming)
- Spring Boot 3.2.2
- MyBatis-Plus
- Jackson (JSON 解析)
- PostgreSQL (知识库)

**Spec:** `docs/superpowers/specs/2026-11-07-workflow-orchestration-design.md`

## Global Constraints

- 必须使用 Spring AI 1.0.0 API（不能升级到 2.x）
- 所有路径必须保持 SSE 流式输出（Flux<ServerSentEvent>）
- 复用现有组件：CoreferenceResolutionService、KnowledgeRetrieval、JobTool、DataSourceTool
- 配置项统一前缀 `workflow.*`
- 意图识别失败时降级为 domain=OTHER（保守策略）

## Review Focus

1. **意图识别 JSON 解析失败** - 模型返回非 JSON 或格式错误时，应降级为 domain=OTHER 并记录日志，不应抛异常导致请求失败
2. **RAG 检索结果为空但未拒答** - KnowledgeRetrieval.Result.refused=false 但 references 为空时，应返回兜底话术而非传空上下文给 Agent
3. **确定性流程中途失败** - 创建成功但提交失败时，应返回明确错误信息（包含已创建的 jobId）并终止流程，不应继续尝试发布
4. **并发请求的 confirmationToken 冲突** - 多个用户同时触发确定性流程时，token 验证应隔离（按 conversationId + userId），不应互相干扰
5. **闲聊/OTHER 类请求的流式格式** - 固定话术也必须包装为 SSE 流（text 事件 + done 事件），不能直接返回字符串

---

### Task 1: 意图分类模型和配置

**Files:**
- Create: `src/main/java/com/dataplatform/workflow/dto/IntentClassificationResult.java`
- Create: `src/main/java/com/dataplatform/workflow/dto/Domain.java`
- Create: `src/main/java/com/dataplatform/workflow/dto/IntentType.java`
- Create: `src/main/java/com/dataplatform/workflow/config/WorkflowProperties.java`
- Modify: `src/main/resources/application.yml` (追加 workflow 配置段)
- Test: `src/test/java/com/dataplatform/workflow/config/WorkflowPropertiesTest.java`

**Interfaces:**
- Produces: 
  - `Domain` enum: `DATAPLATFORM_MANAGER, QA, CHITCHAT, OTHER`
  - `IntentType` enum: `CREATE_SUBMIT_PUBLISH, OTHER, NONE`
  - `IntentClassificationResult` record: `Domain domain(), IntentType intentType()`
  - `WorkflowProperties` 配置类，字段：`intentClassification.enabled`, `rag.vectorSimThreshold`, `rag.rerankThreshold`, `deterministic.requireConfirmation`, `responses.chitchat`, `responses.other`

- [ ] **Step 1: 创建 Domain 枚举 `src/main/java/com/dataplatform/workflow/dto/Domain.java`**

```java
package com.dataplatform.workflow.dto;

public enum Domain {
    DATAPLATFORM_MANAGER,
    QA,
    CHITCHAT,
    OTHER
}
```

- [ ] **Step 2: 创建 IntentType 枚举 `src/main/java/com/dataplatform/workflow/dto/IntentType.java`**

```java
package com.dataplatform.workflow.dto;

public enum IntentType {
    CREATE_SUBMIT_PUBLISH,
    OTHER,
    NONE
}
```

- [ ] **Step 3: 创建 IntentClassificationResult record `src/main/java/com/dataplatform/workflow/dto/IntentClassificationResult.java`**

```java
package com.dataplatform.workflow.dto;

public record IntentClassificationResult(Domain domain, IntentType intentType) {
    
    public static IntentClassificationResult fallback() {
        return new IntentClassificationResult(Domain.OTHER, IntentType.NONE);
    }
}
```

- [ ] **Step 4: 创建配置类 `src/main/java/com/dataplatform/workflow/config/WorkflowProperties.java`**

使用 `@ConfigurationProperties(prefix = "workflow")`，包含嵌套类 `IntentClassification`、`Rag`、`Deterministic`、`Responses`，字段参考 spec 的"配置"章节。

- [ ] **Step 5: 在 application.yml 追加 workflow 配置段**

```yaml
workflow:
  intent-classification:
    enabled: true
  rag:
    vector-sim-threshold: 0.5
    rerank-threshold: 0.6
  deterministic:
    require-confirmation: true
  responses:
    chitchat: "很高兴和你聊天！有什么数据平台相关的问题可以问我。"
    other: "抱歉，我只能回答数据平台相关的问题。"
```

- [ ] **Step 6: 编写配置加载测试 `src/test/java/com/dataplatform/workflow/config/WorkflowPropertiesTest.java`**

```java
@Test
void testWorkflowPropertiesLoaded() {
    assertTrue(properties.getIntentClassification().isEnabled());
    assertEquals(0.6, properties.getRag().getRerankThreshold());
    assertNotNull(properties.getResponses().getChitchat());
}
```

- [ ] **Step 7: 运行测试验证配置加载**

Run: `mvn test -Dtest=WorkflowPropertiesTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/dataplatform/workflow/dto/* src/main/java/com/dataplatform/workflow/config/* src/main/resources/application.yml src/test/java/com/dataplatform/workflow/config/*
git commit -m "feat(workflow): add intent classification DTOs and config"
```

---

### Task 2: 意图识别服务

**Files:**
- Create: `src/main/java/com/dataplatform/workflow/service/IntentClassificationService.java`
- Test: `src/test/java/com/dataplatform/workflow/service/IntentClassificationServiceTest.java`
- Create: `src/test/resources/intent-classification-test-cases.json` (20 条标注样例)

**Interfaces:**
- Consumes: `IntentClassificationResult`, `Domain`, `IntentType` (from Task 1), `WorkflowProperties` (from Task 1), Spring AI `ChatClient`
- Produces: `IntentClassificationService.classify(String message): IntentClassificationResult`

- [ ] **Step 1: 创建测试样例文件 `src/test/resources/intent-classification-test-cases.json`**

包含 20 条样例，格式：
```json
[
  {"input": "Spark 的 executor 内存怎么设置？", "expectedDomain": "QA", "expectedIntentType": "NONE"},
  {"input": "查询作业 ID 为 3 的详情", "expectedDomain": "DATAPLATFORM_MANAGER", "expectedIntentType": "OTHER"},
  {"input": "帮我创建、提交并发布作业 user_sync", "expectedDomain": "DATAPLATFORM_MANAGER", "expectedIntentType": "CREATE_SUBMIT_PUBLISH"},
  {"input": "你好", "expectedDomain": "CHITCHAT", "expectedIntentType": "NONE"},
  {"input": "今天天气怎么样", "expectedDomain": "OTHER", "expectedIntentType": "NONE"}
]
```

覆盖 QA(5条)、DATAPLATFORM_MANAGER/CREATE_SUBMIT_PUBLISH(3条)、DATAPLATFORM_MANAGER/OTHER(7条)、CHITCHAT(2条)、OTHER(3条)。

- [ ] **Step 2: 编写失败测试 `src/test/java/com/dataplatform/workflow/service/IntentClassificationServiceTest.java`**

```java
@Test
void testIntentClassificationAccuracy() throws Exception {
    // 读取测试样例
    ObjectMapper mapper = new ObjectMapper();
    List<TestCase> cases = mapper.readValue(
        getClass().getResourceAsStream("/intent-classification-test-cases.json"),
        new TypeReference<List<TestCase>>() {}
    );
    
    int correct = 0;
    for (TestCase testCase : cases) {
        IntentClassificationResult result = service.classify(testCase.input());
        if (result.domain() == testCase.expectedDomain() 
            && result.intentType() == testCase.expectedIntentType()) {
            correct++;
        }
    }
    
    double accuracy = (double) correct / cases.size();
    assertTrue(accuracy >= 0.8, "Accuracy: " + accuracy + " (expected >= 0.8)");
}

record TestCase(String input, Domain expectedDomain, IntentType expectedIntentType) {}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn test -Dtest=IntentClassificationServiceTest`
Expected: FAIL with "IntentClassificationService not found"

- [ ] **Step 4: 实现 IntentClassificationService `src/main/java/com/dataplatform/workflow/service/IntentClassificationService.java`**

使用 Spring AI ChatClient，system prompt 参考 spec 的"IntentClassificationService"章节。调用 `chatClient.prompt().system(PROMPT).user(message).call().content()`，用 Jackson 解析 JSON。解析失败时调用 `IntentClassificationResult.fallback()` 并记录 warn 日志。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -Dtest=IntentClassificationServiceTest`
Expected: PASS（准确率 ≥ 80%）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/dataplatform/workflow/service/IntentClassificationService.java src/test/java/com/dataplatform/workflow/service/IntentClassificationServiceTest.java src/test/resources/intent-classification-test-cases.json
git commit -m "feat(workflow): implement intent classification service"
```

---

### Task 3: 编排服务骨架和固定话术路径

**Files:**
- Create: `src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java`
- Test: `src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceTest.java`

**Interfaces:**
- Consumes: `IntentClassificationService` (from Task 2), `IntentClassificationResult`, `Domain`, `IntentType` (from Task 1), `WorkflowProperties` (from Task 1)
- Produces: `WorkflowOrchestrationService.orchestrate(String resolvedMessage, String originalMessage, Long conversationId, String userId, String confirmationToken): Flux<ServerSentEvent<Map<String, Object>>>`

- [ ] **Step 1: 编写固定话术路径测试 `src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceTest.java`**

```java
@Test
void testChitchatReturnsFixedResponse() {
    Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
        "你好", "你好", 1L, "user1", null
    );
    
    List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
    assertEquals(2, events.size()); // text + done
    assertEquals("text", events.get(0).event());
    assertTrue(events.get(0).data().get("content").toString().contains("聊天"));
    assertEquals("done", events.get(1).event());
}

@Test
void testOtherDomainReturnsFixedResponse() {
    Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
        "今天天气怎么样", "今天天气怎么样", 1L, "user1", null
    );
    
    List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
    assertEquals("text", events.get(0).event());
    assertTrue(events.get(0).data().get("content").toString().contains("抱歉"));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=WorkflowOrchestrationServiceTest`
Expected: FAIL with "WorkflowOrchestrationService not found"

- [ ] **Step 3: 实现 WorkflowOrchestrationService `src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java`**

在 orchestrate 方法中：
1. 调用 `intentClassificationService.classify(resolvedMessage)` 获取意图
2. 使用 switch 表达式分流：
   - `case CHITCHAT -> returnFixedResponse(properties.getResponses().getChitchat())`
   - `case OTHER -> returnFixedResponse(properties.getResponses().getOther())`
   - `case QA, DATAPLATFORM_MANAGER -> Flux.error(new UnsupportedOperationException("Not implemented"))`（占位符）
3. returnFixedResponse 方法返回 Flux，包含 text 事件和 done 事件

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=WorkflowOrchestrationServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceTest.java
git commit -m "feat(workflow): add orchestration service skeleton with fixed responses"
```

---

### Task 4: QA 路径 - RAG 检索和置信度闸

**Files:**
- Modify: `src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java` (实现 handleQA 方法)
- Test: `src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceQATest.java`

**Interfaces:**
- Consumes: `KnowledgeRetrieval.retrieve()`, `KnowledgeRetrieval.saveCitations()`, `WorkflowProperties.rag.*` (from Task 1)
- Produces: handleQA 方法处理 QA 域请求，返回 Flux<ServerSentEvent>

- [ ] **Step 1: 编写 QA 路径测试 `src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceQATest.java`**

```java
@Test
void testQAWithHighConfidence_CallsAgent() {
    // Mock KnowledgeRetrieval.retrieve() 返回 refused=false
    when(knowledgeRetrieval.retrieve(anyString(), anyLong(), eq(0.5), eq(0.6)))
        .thenReturn(new KnowledgeRetrieval.Result(
            false, null, 
            "[参考资料 1]\nSpark executor 内存...\n用户问题: Spark内存怎么设置", 
            List.of(mockRerankResult()), 
            0.8
        ));
    
    Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
        "Spark内存怎么设置", "Spark内存怎么设置", 1L, "user1", null
    );
    
    // 验证返回流式内容（Agent 生成）
    List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
    assertTrue(events.size() > 0);
    // 验证调用了 saveCitations
    verify(knowledgeRetrieval, times(1)).saveCitations(eq(1L), anyList());
}

@Test
void testQAWithLowConfidence_ReturnsRefusal() {
    // Mock KnowledgeRetrieval.retrieve() 返回 refused=true
    when(knowledgeRetrieval.retrieve(anyString(), anyLong(), eq(0.5), eq(0.6)))
        .thenReturn(KnowledgeRetrieval.Result.refused("抱歉，知识库中没有相关信息"));
    
    Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
        "未知问题", "未知问题", 1L, "user1", null
    );
    
    List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
    assertEquals("text", events.get(0).event());
    assertTrue(events.get(0).data().get("content").toString().contains("抱歉"));
    // 验证未调用 saveCitations
    verify(knowledgeRetrieval, never()).saveCitations(anyLong(), anyList());
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=WorkflowOrchestrationServiceQATest`
Expected: FAIL with "handleQA not implemented"

- [ ] **Step 3: 在 WorkflowOrchestrationService 中实现 handleQA 方法**

逻辑参考 spec "QA 类处理（handleQA）"章节：
1. 调用 `knowledgeRetrieval.retrieve(resolvedMessage, conversationId, vectorSimThreshold, rerankThreshold)`
2. 如果 `result.refused()` 返回 true，调用 `returnFixedResponse(result.refusalMessage())`
3. 否则调用 `callAgentStream(result.prompt(), conversationId, userId, null, result.references())`，其中：
   - result.prompt() 作为 user message 传入（已包含参考资料格式）
   - Agent 只配置 JobTool、DataSourceTool（不含 FaqTool）
   - 流式返回后 doOnComplete 调用 `knowledgeRetrieval.saveCitations(conversationId, result.references())`

callAgentStream 方法使用 ChatClient.builder，参考 AiAssistantController.chatStream 的逻辑。

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=WorkflowOrchestrationServiceQATest`
Expected: PASS

- [ ] **Step 5: 集成测试 - 手动验证**

启动应用，调用 `/api/ai/conversations/1/chat/stream?message=Spark参数怎么调优`，检查日志是否显示：
```
IntentClassificationService → QA
KnowledgeRetrieval.retrieve called
置信度闸通过
Agent 生成答案
saveCitations called
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceQATest.java
git commit -m "feat(workflow): implement QA path with RAG and confidence gate"
```

---

### Task 5: Agent 自主决策路径

**Files:**
- Modify: `src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java` (实现 handleAgentWithTools 方法)
- Test: `src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceAgentTest.java`

**Interfaces:**
- Consumes: `JobTool`, `DataSourceTool`, `FaqTool` (现有工具), `ChatClient` (Spring AI), 已实现的 callAgentStream 方法
- Produces: handleAgentWithTools 方法处理 DATAPLATFORM_MANAGER/OTHER 请求

- [ ] **Step 1: 编写 Agent 路径测试 `src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceAgentTest.java`**

```java
@Test
void testAgentPath_CallsToolsDirectly() {
    // Mock IntentClassificationService 返回 DATAPLATFORM_MANAGER/OTHER
    when(intentClassificationService.classify(anyString()))
        .thenReturn(new IntentClassificationResult(Domain.DATAPLATFORM_MANAGER, IntentType.OTHER));
    
    Flux<ServerSentEvent<Map<String, Object>>> result = service.orchestrate(
        "查询作业 ID 为 3", "查询作业 ID 为 3", 1L, "user1", null
    );
    
    // 验证返回流式内容（Agent 调用工具）
    List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
    assertTrue(events.size() > 0);
    // 验证未调用 KnowledgeRetrieval（Agent 路径不预检索）
    verify(knowledgeRetrieval, never()).retrieve(anyString(), anyLong(), anyDouble(), anyDouble());
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=WorkflowOrchestrationServiceAgentTest`
Expected: FAIL with "handleAgentWithTools not implemented"

- [ ] **Step 3: 在 WorkflowOrchestrationService 中实现 handleAgentWithTools 方法**

调用 `callAgentStream(resolvedMessage, conversationId, userId, confirmationToken, null)`，其中：
- 传入所有工具：JobTool, DataSourceTool, FaqTool
- 使用 resolvedMessage 作为 user message（无额外上下文注入）

- [ ] **Step 4: 在 orchestrate 的 switch 中补充分流逻辑**

```java
case DATAPLATFORM_MANAGER -> {
    if (intent.intentType() == IntentType.CREATE_SUBMIT_PUBLISH) {
        yield handleDeterministicWorkflow(...); // Task 6 实现
    } else {
        yield handleAgentWithTools(...);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -Dtest=WorkflowOrchestrationServiceAgentTest`
Expected: PASS

- [ ] **Step 6: 集成测试 - 手动验证**

调用 `/api/ai/conversations/1/chat/stream?message=查询作业ID为3`，检查日志：
```
IntentClassificationService → DATAPLATFORM_MANAGER/OTHER
handleAgentWithTools called
Agent 调用 JobTool.getJobDetails
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java src/test/java/com/dataplatform/workflow/service/WorkflowOrchestrationServiceAgentTest.java
git commit -m "feat(workflow): implement agent self-decision path"
```

---

### Task 6: 确定性流程 - 参数提取和三步串行

**Files:**
- Create: `src/main/java/com/dataplatform/workflow/service/DeterministicWorkflowService.java`
- Create: `src/main/java/com/dataplatform/workflow/dto/JobCreationParams.java`
- Create: `src/main/java/com/dataplatform/workflow/service/ConfirmationTokenService.java`
- Modify: `src/main/java/com/dataplatform/workflow/service/WorkflowOrchestrationService.java` (实现 handleDeterministicWorkflow 方法)
- Test: `src/test/java/com/dataplatform/workflow/service/DeterministicWorkflowServiceTest.java`

**Interfaces:**
- Consumes: `JobService` (现有), `ChatClient` (参数提取), `ConfirmationTokenService.generate()`, `ConfirmationTokenService.validate()`
- Produces: 
  - `DeterministicWorkflowService.execute(String message, Long conversationId, String userId, String confirmationToken): Flux<ServerSentEvent<Map<String, Object>>>`
  - `ConfirmationTokenService.generate(Long conversationId, String userId, String action, Map<String, Object> context): String`
  - `ConfirmationTokenService.validate(String token, Long conversationId, String userId): boolean`

- [ ] **Step 1: 创建参数 DTO `src/main/java/com/dataplatform/workflow/dto/JobCreationParams.java`**

```java
package com.dataplatform.workflow.dto;

import java.util.List;

public record JobCreationParams(
    String jobCode,
    String jobName,
    String jobType,
    String sourceDatasourceCode,
    String sourceDatabase,
    String sourceTable,
    String targetDatabase,
    String engineType,
    List<String> missingParams
) {
    public boolean isComplete() {
        return missingParams == null || missingParams.isEmpty();
    }
}
```

- [ ] **Step 2: 创建 ConfirmationTokenService `src/main/java/com/dataplatform/workflow/service/ConfirmationTokenService.java`**

使用 UUID.randomUUID() 生成 token，存储到 Redis（key: `confirmation:{token}`, value: JSON 包含 conversationId、userId、action、context，过期时间 5 分钟）。validate 方法检查 token 是否存在且 conversationId、userId 匹配，验证后删除 token（一次性使用）。

- [ ] **Step 3: 编写参数提取测试 `src/test/java/com/dataplatform/workflow/service/DeterministicWorkflowServiceTest.java`**

```java
@Test
void testExtractParams_Complete() {
    String message = "帮我创建、提交并发布作业，源数据源是 ds001，源表是 user_info，目标库是 dw";
    
    JobCreationParams params = service.extractParams(message);
    
    assertNotNull(params.jobCode());
    assertTrue(params.isComplete());
    assertEquals("ds001", params.sourceDatasourceCode());
}

@Test
void testExtractParams_Missing() {
    String message = "创建作业 user_sync";
    
    JobCreationParams params = service.extractParams(message);
    
    assertFalse(params.isComplete());
    assertTrue(params.missingParams().contains("sourceDatasourceCode"));
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `mvn test -Dtest=DeterministicWorkflowServiceTest::testExtractParams*`
Expected: FAIL

- [ ] **Step 5: 实现 DeterministicWorkflowService.extractParams 方法**

使用 ChatClient 调用模型，system prompt 参考 spec "参数提取 prompt"章节，返回 JSON，用 Jackson 解析为 JobCreationParams。

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -Dtest=DeterministicWorkflowServiceTest::testExtractParams*`
Expected: PASS

- [ ] **Step 7: 编写三步流程测试**

```java
@Test
void testExecute_FullFlow_WithConfirmation() {
    String message = "创建、提交并发布作业 user_sync，源数据源 ds001，源表 user_info，目标库 dw";
    
    // 第一次调用：无 token，返回 need_confirmation
    Flux<ServerSentEvent<Map<String, Object>>> result1 = service.execute(message, 1L, "user1", null);
    List<ServerSentEvent<Map<String, Object>>> events1 = result1.collectList().block();
    
    ServerSentEvent<Map<String, Object>> confirmEvent = events1.stream()
        .filter(e -> "need_confirmation".equals(e.event()))
        .findFirst().orElseThrow();
    String token = (String) confirmEvent.data().get("token");
    
    // 第二次调用：有 token，完成发布
    Flux<ServerSentEvent<Map<String, Object>>> result2 = service.execute(message, 1L, "user1", token);
    List<ServerSentEvent<Map<String, Object>>> events2 = result2.collectList().block();
    
    assertTrue(events2.stream().anyMatch(e -> "workflow_complete".equals(e.event())));
    verify(jobService, times(1)).createJob(any());
    verify(jobService, times(1)).publishJob(anyLong());
}

@Test
void testExecute_CreateFails_StopsWorkflow() {
    when(jobService.createJob(any())).thenThrow(new RuntimeException("数据源不存在"));
    
    Flux<ServerSentEvent<Map<String, Object>>> result = service.execute("...", 1L, "user1", null);
    
    List<ServerSentEvent<Map<String, Object>>> events = result.collectList().block();
    assertTrue(events.stream().anyMatch(e -> "error".equals(e.event())));
    verify(jobService, never()).publishJob(anyLong());
}
```

- [ ] **Step 8: 运行测试验证失败**

Run: `mvn test -Dtest=DeterministicWorkflowServiceTest::testExecute*`
Expected: FAIL

- [ ] **Step 9: 实现 DeterministicWorkflowService.execute 方法**

流程参考 spec "确定性流程（handleDeterministicWorkflow）"章节：
1. extractParams
2. 如果 !params.isComplete()，返回 error 事件 "缺少参数：{missingParams}"
3. 调用 jobService.createJob，成功后发送 step_complete 事件 {step: "create"}
4. 调用 jobService.submitJob（假设方法存在或调用 updateJob 设置状态），成功后发送 step_complete 事件 {step: "submit"}
5. 检查 confirmationToken：
   - 如果为 null，生成 token，发送 need_confirmation 事件 {action: "publish", token, jobId}，结束流
   - 如果不为 null，验证 token，验证通过后调用 jobService.publishJob，发送 workflow_complete 事件

- [ ] **Step 10: 运行测试验证通过**

Run: `mvn test -Dtest=DeterministicWorkflowServiceTest`
Expected: PASS

- [ ] **Step 11: 在 WorkflowOrchestrationService 中实现 handleDeterministicWorkflow 方法**

调用 `deterministicWorkflowService.execute(resolvedMessage, conversationId, userId, confirmationToken)`。

- [ ] **Step 12: 集成测试 - 手动验证**

调用 `/api/ai/conversations/1/chat/stream?message=创建提交并发布作业 test_job`，检查：
1. 返回 need_confirmation 事件
2. 携带 token 重新请求，完成发布

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/dataplatform/workflow/service/DeterministicWorkflowService.java src/main/java/com/dataplatform/workflow/dto/JobCreationParams.java src/main/java/com/dataplatform/workflow/service/ConfirmationTokenService.java src/test/java/com/dataplatform/workflow/service/DeterministicWorkflowServiceTest.java
git commit -m "feat(workflow): implement deterministic three-step workflow"
```

---

### Task 7: 集成到 AiAssistantController

**Files:**
- Modify: `src/main/java/com/dataplatform/controller/AiAssistantController.java` (在 chatStream 中调用 WorkflowOrchestrationService)
- Test: 手动集成测试（验收标准）

**Interfaces:**
- Consumes: `WorkflowOrchestrationService.orchestrate()` (from Task 3-6), `WorkflowProperties.intentClassification.enabled` (from Task 1)

- [ ] **Step 1: 在 AiAssistantController.chatStream 中集成编排服务**

在指代消解后（第 197 行之后），插入：
```java
// 检查 workflow 开关
if (workflowProperties.getIntentClassification().isEnabled()) {
    return workflowOrchestrationService.orchestrate(
        resolvedMessage, originalMessage, finalConversationId, userId, confirmationToken
    );
}
```

保留原有 Agent 逻辑作为降级分支（开关关闭时走原流程）。

- [ ] **Step 2: 注入 WorkflowOrchestrationService 到 AiAssistantController**

在构造函数中添加参数 `WorkflowOrchestrationService workflowOrchestrationService, WorkflowProperties workflowProperties`。

- [ ] **Step 3: 启动应用验证编译通过**

Run: `mvn clean compile`
Expected: SUCCESS

- [ ] **Step 4: 验收测试 1 - Spark 参数调优问题**

请求: `GET /api/ai/conversations/1/chat/stream?message=Spark的executor内存怎么设置&userId=test`

检查日志包含：
```
IntentClassificationService → QA
KnowledgeRetrieval.retrieve called
置信度闸通过 (或拒答)
Agent 生成答案 (如果通过)
```

- [ ] **Step 5: 验收测试 2 - 查询作业**

请求: `GET /api/ai/conversations/1/chat/stream?message=查询作业ID为3&userId=test`

检查日志包含：
```
IntentClassificationService → DATAPLATFORM_MANAGER/OTHER
handleAgentWithTools called
Agent 调用工具
```

- [ ] **Step 6: 验收测试 3 - 闲聊**

请求: `GET /api/ai/conversations/1/chat/stream?message=你好&userId=test`

响应应秒返固定话术 "很高兴和你聊天！有什么数据平台相关的问题可以问我。"，日志无 Agent 调用。

- [ ] **Step 7: 验收测试 4 - 创建提交发布**

请求: `GET /api/ai/conversations/1/chat/stream?message=创建、提交并发布作业user_sync&userId=test`

响应应包含 need_confirmation 事件，携带 token 重新请求后完成发布。

- [ ] **Step 8: 更新 dev-notes/ch05.md 记录集成完成**

追加：
```markdown
### 2026-11-07 - 集成完成

**关键产出**：
- WorkflowOrchestrationService 已集成到 AiAssistantController
- 4 条验收测试通过

**翻车与返工**：
- （记录实际遇到的问题）
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/dataplatform/controller/AiAssistantController.java dev-notes/ch05.md
git commit -m "feat(workflow): integrate orchestration service into AI assistant"
```

---

### Task 8: 前端适配 - 确认按钮和步骤进度

**Files:**
- Modify: `src/main/resources/static/chat.html` (或前端 JS 文件)

**Interfaces:**
- Consumes: SSE 事件 `need_confirmation`, `step_complete`, `workflow_complete` (from Task 6)

- [ ] **Step 1: 在前端 EventSource 监听中添加 need_confirmation 事件处理**

```javascript
eventSource.addEventListener('need_confirmation', (e) => {
    const data = JSON.parse(e.data);
    const token = data.token;
    const action = data.action;
    
    // 显示确认按钮
    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = `确认${action === 'publish' ? '发布' : '操作'}`;
    confirmBtn.onclick = () => {
        // 重新发起请求，携带 token
        fetch(`/api/ai/conversations/${conversationId}/chat/stream?message=${lastMessage}&userId=${userId}`, {
            headers: { 'X-Confirmation-Token': token }
        });
    };
    chatContainer.appendChild(confirmBtn);
});
```

- [ ] **Step 2: 添加 step_complete 事件处理**

```javascript
eventSource.addEventListener('step_complete', (e) => {
    const data = JSON.parse(e.data);
    appendMessage(`✓ ${data.message}`, 'system');
});
```

- [ ] **Step 3: 手动测试前端确认流程**

触发确定性流程，验证：
1. 显示确认按钮
2. 点击按钮后发起新请求
3. 显示步骤进度（"正在创建作业... ✓"）

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/chat.html
git commit -m "feat(workflow): add frontend support for confirmation and progress"
```

---

## 实现顺序总结

1. Task 1-2：意图识别基础设施（DTO、配置、服务）
2. Task 3：编排服务骨架和固定话术（最简单路径先通）
3. Task 4：QA 路径（复用现有 RAG）
4. Task 5：Agent 路径（复用现有 Agent）
5. Task 6：确定性流程（最复杂，依赖参数提取和确认机制）
6. Task 7：集成到 Controller（串联所有路径）
7. Task 8：前端适配（完整闭环）

每个 Task 都是独立可测试的，按顺序实现可以逐步验证功能。
