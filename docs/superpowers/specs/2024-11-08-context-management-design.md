# 会话上下文管理设计文档
**文档日期**: 2024-11-08  
**技术栈**: Spring AI 1.0.0 + Spring Boot 3.2.2 + MyBatis

## 1. 设计目标

将数据平台智能客服的简单滑窗升级为三层上下文管理：
- **Layer 0**（摘要层）：异步压缩最远的对话成分段梗概
- **Layer 2**（半压层）：规则截短中间对话，保留用户原话，截短助手回复和工具结果
- **Layer 1**（原文层）：保留最近几轮完整对话

**约束**：
- 只管当前会话，不跨会话，不存用户画像
- token 不爆，默认窗口下 20 轮不触发压缩
- 小窗口配置下触发完整降级链路
- 该功能只在作业列表页的智能助手添加 不要搞错了

## 2. 架构设计

### 2.1 三层结构边界定义

两个锚点划分三层，用消息 ID 表达边界，不搬数据：

```
id ≤ summary_upto_msg_id           Layer 0: 已摘要
summary_upto < id ≤ layer1_from    Layer 2: 半压
id > layer1_from                   Layer 1: 原文
```

- `summary_upto_msg_id`: 摘要已覆盖到哪条消息（`conversations` 表）
- `layer1_from_msg_id`: Layer 1 起点（`conversations` 表）
- 降级操作只挪这两个 ID，不修改 `messages` 表数据

###  2.2 数据持久化

**三张表** (MyBatis 管理):
1. `conversations`: 会话元数据，新增 `summary`、`summary_upto_msg_id`、`layer1_from_msg_id`
2. `messages`: 完整消息历史（已存在）
3. `conversation_summaries`: 分段摘要，一段一行，只追加

**职责分离**:
- **MyBatis Mapper**: 管理三张表的完整历史读写
- **ChatMemoryRepository** (自定义 MyBatis 实现): 只管"当前窗口内给模型看什么"，不持久化完整历史
- Spring AI 的 `MessageWindowChatMemory` 管理窗口大小，底层委托自定义 `ChatMemoryRepository`

### 2.3 上下文拼装顺序

自定义 Advisor 在 `adviseCall` / `adviseStream` 中按固定顺序拼装：

```
1. System 人设 + 红线 + 工具定义 (固定，每轮相同)
2. Layer 2 截短对话 (中间层)
3. Layer 1 原文对话 (最近几轮)
4. 用户当前输入
5. Layer 0 梗概 + 检索证据 (合成一条挂在用户输入后)
```

**Prompt Caching 策略**:
- 用供应商对应的缓存策略（如 `AnthropicCacheStrategy.SYSTEM_AND_TOOLS`）
- 缓存断点放在工具定义和系统提示的稳定前缀上
- 动态尾部（历史对话）变化不冲掉缓存

### 2.4 Token 预算计算

**从模型窗口倒推**:
```
history_budget = min(
  想留住的轮数 × 每轮稳态占用,
  MODEL_WINDOW - system - retrieval - summary - output - safety - react_peak
)

layer1_budget = history_budget × 0.7
layer2_budget = history_budget × 0.3
```

**启动自检**:
- 检查连一轮都装不下就报警
- 中文按字数折 token 的口径要和预算一起校准

## 3. 核心流程

### 3.1 Layer 2 → Layer 0 降级 (半压层超预算)

**触发**: Layer 2 累计 token 超预算  
**动作**:
1. 在后台异步触发摘要任务 (`@Async("summaryExecutor")`)
2. 摘要任务读取 `(summary_upto_msg_id, layer1_from_msg_id]` 区间消息
3. 调 LLM 生成摘要（提炼事实、诉求、项目编号、数据集名，长度几十到一两百字）
4. 追加新段落到 `conversation_summaries` (seq 自增)
5. 更新 `conversations.summary_upto_msg_id = layer1_from_msg_id`
6. 拼接所有摘要段落更新 `conversations.summary` 字段

**不阻塞**: 摘要生成期间，当前轮回复正常返回

### 3.2 Layer 1 → Layer 2 降级 (原文层超预算)

**触发**: Layer 1 累计 token 超预算  
**动作**:
1. 同步挪动 `layer1_from_msg_id` 向后（降级 N 条消息）
2. 被降级的消息渲染成半压形态：
   - 用户消息：保留原文
   - 助手消息：只保留开头 50 字
   - 工具结果：一行标识 `[工具调用结果，已省略]`

**日志**: `layer1 degrade X→Y`

### 3.3 摘要提示词

```
你是专业的对话摘要助手。提炼以下对话中的事实、诉求和关键信息：
- 明确记录：项目编号、数据集名、表名、明确诉求
- 提炼还没解决的问题
- 寒暄闲聊不保留
- 对话中没出现的内容一个字不许编
- 长度控制在 50-200 字

对话内容：
{layer2_messages}
```

## 4. 会话隔离

**多会话支持**:
- `conversationId` 由服务端从当前用户 + 会话派生（不用固定值）
- 运行时通过 `ChatClient.prompt().advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))` 传入
- `ChatMemory` 操作和 MyBatis 查询都以 `conversationId` 为条件

**前端侧栏**:
- 能开多个会话对照
- 切回旧会话时历史完整回载
- 能接着聊

## 5. 可观测性

**日志埋点** (`log/app.log`):

### 5.1 模型上下文日志 (`model_ctx`)
**位置**: 自定义 Advisor 的 `adviseCall` / `adviseStream`  
**内容**:
```
[model_ctx] conversationId={} windowSize={} estimatedTokens={}
Summary: {}
Messages:
  [user] {}
  [assistant] {}
  ...
```

### 5.2 历史上下文日志 (`history_ctx`)
**位置**: 历史组装逻辑  
**内容**:
```
[history_ctx] conversationId={} summaryLines={} messageCount={}
Summary: {}
Recent: [user] ..., [assistant] ...
```
**要求**: 每轮必打，包括不进 Agent 的闲聊兜底轮

### 5.3 摘要任务日志
```
[summary trigger] layer2 ≈{estTokens} token > budget {budget}
[summary start] conversationId={} from={} upto={}
[summary done] segment {} elapsed={}ms
[summary skip] reason={}
[summary error] {}
```

## 6. 技术实现要点

### 6.1 自定义 ChatMemoryRepository (MyBatis 实现)

```java
public class MyBatisChatMemoryRepository implements ChatMemoryRepository {
    private final MessageMapper messageMapper;
    
    @Override
    public void add(String conversationId, List<Message> messages) {
        // 写入 messages 表 (conversationId 条件)
    }
    
    @Override
    public List<Message> get(String conversationId, int lastN) {
        // 从 messages 表读取最近 N 条 (conversationId 条件)
    }
    
    @Override
    public void clear(String conversationId) {
        // 清空 conversationId 下的消息 (软删除或物理删除)
    }
}
```

### 6.2 自定义 Advisor (三层拼装 + 日志)

```java
public class ContextLayeredAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        String conversationId = (String) request.adviseContext()
            .get(ChatMemory.CONVERSATION_ID);
        
        // 1. 计算 token 预算
        // 2. 读取三层消息 (MyBatis)
        // 3. 检查 Layer 1 / Layer 2 预算，触发降级
        // 4. 拼装最终上下文
        // 5. 打印 model_ctx 日志
        // 6. 修改 request 的 messages
        
        return chain.nextAroundCall(modifiedRequest);
    }
    
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest request, StreamAroundAdvisorChain chain) {
        // 同 aroundCall 逻辑
    }
}
```

### 6.3 异步摘要任务

```java
@Service
public class SummaryService {
    
    @Async("summaryExecutor")
    public CompletableFuture<Void> summarizeLayer2(String conversationId, Long fromMsgId, Long uptoMsgId) {
        log.info("[summary start] conversationId={} from={} upto={}", ...);
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 读取消息区间
            List<Message> messages = messageMapper.selectByIdRange(conversationId, fromMsgId, uptoMsgId);
            
            // 2. 调 LLM 生成摘要
            String summary = chatClient.prompt()
                .user(buildSummaryPrompt(messages))
                .call()
                .content();
            
            // 3. 追加 conversation_summaries
            summaryMapper.insert(new ConversationSummary(...));
            
            // 4. 更新 conversations.summary_upto_msg_id 和 summary
            conversationMapper.updateSummaryAnchor(conversationId, uptoMsgId);
            
            log.info("[summary done] segment {} elapsed={}ms", seq, elapsed);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("[summary error] {}", e.getMessage(), e);
            throw e;
        }
    }
}
```

### 6.4 自定义线程池

```java
@Configuration
public class AsyncConfig {
    
    @Bean(name = "summaryExecutor")
    public Executor summaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("summary-");
        executor.initialize();
        return executor;
    }
}
```

## 7. 验收标准

1. **20 轮不爆**: 默认窗口配置下连续聊 20 轮，token 不爆，系统不崩

2. **小窗口触发降级**: 配置 `MODEL_CONTEXT_WINDOW=18000` 等参数，日志出现：
   - `layer1 degrade X→Y`
   - `summary trigger layer2 ≈N token > budget M`
   - `summary done segment N`
   - 此时问"最开始那个项目/数据集后来怎么说"，能靠梗概答对

3. **大窗口不压缩**: 默认窗口下聊 20 轮，不触发任何降级和摘要

4. **异步不阻塞**: 日志显示摘要生成没有阻塞当前轮用户回复

5. **多会话隔离**: 前端侧栏开多个会话对照、切回旧会话历史完整

## 8. 非目标

- 语义检索捞历史
- 主题重要度留关键事实
- 跨会话长期记忆
- 用户画像
- 不引入 Spring AI Session
- 不使用自动配置的 `JdbcChatMemoryRepository`

## 9. 实现计划

### Phase 1: 数据层
- 执行建表 DDL (conversations 加三列、conversation_summaries 新表)
- 创建 MyBatis Mapper (ConversationMapper、MessageMapper、ConversationSummaryMapper 扩展)
- 实现自定义 `MyBatisChatMemoryRepository`

### Phase 2: Token 预算与三层裁剪
- 实现 token 预算计算逻辑
- 实现三层消息读取与渲染逻辑 (Layer 1 原文、Layer 2 半压)
- 单元测试验证预算计算和半压规则

### Phase 3: 自定义 Advisor
- 实现 `ContextLayeredAdvisor` (三层拼装 + model_ctx 日志)
- 实现 Layer 1 → Layer 2 同步降级
- 集成到 ChatClient

### Phase 4: 异步摘要
- 配置 `@Async("summaryExecutor")` 线程池
- 实现 `SummaryService` (摘要生成 + summary_ctx 日志)
- 实现 Layer 2 → Layer 0 异步降级触发

### Phase 5: 集成测试
- 默认窗口 20 轮测试（不触发压缩）
- 小窗口配置测试（触发完整降级链路）
- 多会话隔离测试
- 日志验证 (model_ctx / history_ctx / summary logs)

## 10. 依赖查询记录

**已查 Context7**:
- `ChatMemoryRepository` 接口定义：`add()`, `get()`, `clear()` 方法
- `MessageWindowChatMemory` builder 用法
- `Advisor` 接口：`CallAroundAdvisor` / `StreamAroundAdvisor`，`adviseCall` / `adviseStream` 方法
- `CONVERSATION_ID` 参数传递：运行时通过 `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))`

**待查**:
- `AnthropicCacheStrategy` 枚举值（需查 Spring AI 对应供应商的缓存策略 API）
