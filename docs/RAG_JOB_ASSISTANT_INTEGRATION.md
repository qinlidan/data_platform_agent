# Job 列表页 AI 助手 RAG 集成方案

## 概述

本文档说明如何在 Job 列表页的 AI 助手中集成知识库检索增强生成（RAG）能力，使助手能够基于平台知识库回答用户问题。

## 架构设计

```
用户问题
   ↓
EnhancedChatService.enhanceWithKnowledgeBase()
   ↓
KnowledgeRetrieval.retrieve()
   ↓
返回 ChatContext（增强的系统提示 + 引用元数据）
   ↓
发送给 AI 模型
   ↓
AI 回答（带引用标记 [1], [2]）
   ↓
前端展示（回答 + 引用气泡）
```

## 核心组件

### 1. EnhancedChatService

**位置**: `com.dataplatform.ai.service.EnhancedChatService`

**职责**: 
- 封装知识库检索逻辑
- 构建增强的系统提示
- 提取引用元数据供前端展示

**核心方法**:

```java
// 方法 1：使用默认阈值（向量相似度 0.7，重排相关度 0.5）
public ChatContext enhanceWithKnowledgeBase(String userQuestion, int topK)

// 方法 2：自定义阈值
public ChatContext enhanceWithKnowledgeBase(
    String userQuestion, 
    int topK, 
    double vectorSimThreshold, 
    double rerankThreshold
)
```

**返回值**: `ChatContext`
- `enhancedSystemPrompt`: 包含知识库内容的增强提示
- `citations`: 引用元数据列表（用于前端渲染引用气泡）
- `hasKnowledgeBase`: 是否成功检索到知识库内容

### 2. KnowledgeRetrieval

**位置**: `com.dataplatform.knowledge.service.KnowledgeRetrieval`

**职责**: 
- 向量检索
- 重排序
- 拒答判断

**核心方法**:

```java
public Result retrieve(
    String question, 
    String conversationId, 
    double vectorSimThreshold, 
    double rerankThreshold
)
```

**返回值**: `Result`
- `refused`: 是否拒答
- `refusalMessage`: 拒答原因
- `prompt`: 格式化的参考资料提示
- `references`: 重排后的文档片段
- `topRelevance`: 最高重排分数

## 使用示例

### 在 Controller 中集成

```java
@RestController
@RequestMapping("/api/ai-assistant")
public class AiAssistantController {

    @Autowired
    private EnhancedChatService enhancedChatService;
    
    @Autowired
    private ChatService chatService;  // 现有的对话服务

    @PostMapping("/job-list/chat")
    public ChatResponse chatWithKnowledgeBase(@RequestBody ChatRequest request) {
        // 1. 检索知识库
        EnhancedChatService.ChatContext context = 
            enhancedChatService.enhanceWithKnowledgeBase(
                request.getUserMessage(), 
                3  // 检索 top 3 文档
            );

        // 2. 构建完整的系统提示
        String fullSystemPrompt = buildSystemPrompt(context);

        // 3. 调用 AI 模型
        String aiResponse = chatService.chat(
            fullSystemPrompt, 
            request.getUserMessage()
        );

        // 4. 提取实际使用的引用
        List<CitationMetadata> usedCitations = 
            enhancedChatService.extractUsedCitations(
                aiResponse, 
                context.getCitations()
            );

        // 5. 返回结果
        return new ChatResponse(aiResponse, usedCitations);
    }

    private String buildSystemPrompt(EnhancedChatService.ChatContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // 基础系统提示
        prompt.append("你是数据平台的 AI 助手...\n\n");
        
        // 添加知识库内容（如果有）
        if (context.hasKnowledgeBase()) {
            prompt.append(context.getEnhancedSystemPrompt());
        }
        
        return prompt.toString();
    }
}
```

### 前端集成示例

```typescript
interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  citations?: Citation[];
}

interface Citation {
  index: number;
  title: string;
  content: string;
  score: number;
  chunkId: number;
}

// 渲染消息（包含引用气泡）
function renderMessage(message: ChatMessage) {
  return (
    <div>
      <div>{message.content}</div>
      {message.citations && message.citations.length > 0 && (
        <div className="citations">
          {message.citations.map(citation => (
            <div key={citation.index} className="citation-bubble">
              <strong>[{citation.index}] {citation.title}</strong>
              <p>{citation.content}</p>
              <small>相关度: {(citation.score * 100).toFixed(1)}%</small>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

## 配置参数

### 推荐配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `topK` | 3 | 检索文档数量（平衡性能和准确性）|
| `vectorSimThreshold` | 0.7 | 向量相似度阈值（筛选粗排结果）|
| `rerankThreshold` | 0.5 | 重排相关度阈值（触发拒答逻辑）|

### 阈值调整指南

- **提高准确性**：提高 `rerankThreshold`（如 0.6），减少低质量回答
- **提高召回率**：降低 `vectorSimThreshold`（如 0.6），增加候选文档
- **平衡性能**：减少 `topK`（如 2），降低重排计算成本

## 拒答机制

当知识库中找不到相关内容时（重排分数 < 0.5），系统会触发拒答：

```java
if (result.refused()) {
    log.info("[EnhancedChat] 知识库检索拒答: {}", result.refusalMessage());
    return ChatContext.empty();
}
```

此时：
- `hasKnowledgeBase()` 返回 `false`
- `enhancedSystemPrompt` 为空字符串
- `citations` 为空列表
- AI 助手将使用基础知识回答问题（不依赖知识库）

## 测试

### 单元测试

```bash
mvn test -Dtest=EnhancedChatServiceTest
```

**测试用例**:
1. `testEnhanceWithKnowledgeBase_Success` - 成功检索场景
2. `testEnhanceWithKnowledgeBase_Refused` - 拒答场景
3. `testExtractUsedCitations` - 引用提取功能

### 集成测试

可以通过以下方式测试完整流程：

1. **Postman / cURL 测试**：
   ```bash
   curl -X POST http://localhost:8080/api/ai-assistant/job-list/chat \
     -H "Content-Type: application/json" \
     -d '{"userMessage": "如何创建数据源？"}'
   ```

2. **前端集成测试**：在 Job 列表页的 AI 助手对话框中输入问题

## 性能优化建议

1. **缓存重排模型**：`RerankService` 已经使用单例模式缓存模型
2. **异步检索**：对于非阻塞场景，可以将检索改为异步执行
3. **结果缓存**：对高频问题缓存检索结果（需要 Redis）

## 故障排查

### 问题 1：检索返回空结果

**可能原因**:
- 知识库中没有相关文档
- 向量相似度阈值过高
- Embedding 模型未正确加载

**排查步骤**:
1. 检查日志：`[EnhancedChat] 知识库检索成功`
2. 查看 `topRelevance` 分数（应该 > 0.5）
3. 降低 `vectorSimThreshold` 重试

### 问题 2：AI 回答没有使用知识库内容

**可能原因**:
- 系统提示构建错误
- AI 模型没有理解引用格式

**排查步骤**:
1. 打印 `enhancedSystemPrompt` 检查内容
2. 检查 AI 回答中是否包含 `[1]`, `[2]` 标记
3. 调整系统提示的引用指导语

### 问题 3：重排服务加载失败

**可能原因**:
- Rerank 模型文件缺失
- 内存不足

**排查步骤**:
1. 检查 `知识库/models/rerank/` 目录
2. 查看 JVM 堆内存设置（-Xmx）
3. 降级使用向量相似度排序（禁用 Rerank）

## 后续优化方向

1. **多轮对话上下文管理**：记录对话历史，支持追问
2. **混合检索**：结合关键词检索和向量检索
3. **实时反馈**：收集用户点赞/点踩数据，优化检索策略
4. **知识图谱增强**：结合实体关系图谱提升回答准确性

## 相关文档

- [RAG 服务设计文档](./RAG_SERVICE_DESIGN.md)
- [知识库检索原理](./KNOWLEDGE_RETRIEVAL.md)
- [重排服务文档](./RERANK_SERVICE.md)

## 更新记录

| 日期 | 版本 | 变更内容 | 负责人 |
|------|------|----------|--------|
| 2026-11-08 | 1.0 | 初始版本 | AI |
