# Task 3-5 实现完成

## 概述

已完成 Task 3-5 的实现，包括：
- **Task 3**: 混合检索（向量 + 关键词 + RRF 融合）
- **Task 4**: 重排序（使用阿里云 DashScope BGE-Reranker）
- **Task 5**: RAG 服务整合 + 拒答逻辑

## 实现的组件

### 1. HybridSearchService (Task 3)
- 位置：`com.dataplatform.knowledge.service.HybridSearchService`
- 功能：混合检索（向量 + 关键词 + RRF 融合）
- 主要方法：
  - `hybridSearch(String queryText, String docType, int topK)`: 混合检索
  - `vectorSearch(String queryText, String docType, int topK)`: 纯向量检索
  - `keywordSearch(String queryText, String docType, int topK)`: 纯关键词检索

### 2. RerankerService (Task 4)
- 位置：`com.dataplatform.knowledge.service.RerankerService`
- 功能：使用 BGE-Reranker 对检索结果重排序
- 主要方法：
  - `rerank(String query, List<SearchResultDTO> results, int topK)`: 重排序

### 3. RAGService (Task 5)
- 位置：`com.dataplatform.knowledge.service.RAGService`
- 功能：整合检索、重排、生成的完整 RAG 流程
- 主要方法：
  - `answer(String query, Long conversationId, double vectorSimThreshold, double rerankThreshold)`: 回答用户问题
- 拒答逻辑：
  - 检索无结果或低于阈值
  - 重排分数过低
  - LLM 生成失败

### 4. PromptBuilder
- 位置：`com.dataplatform.knowledge.service.PromptBuilder`
- 功能：构建包含引用的 Prompt
- 布局策略：最相关的放开头，次相关的放结尾

### 5. RAGController
- 位置：`com.dataplatform.knowledge.controller.RAGController`
- 功能：暴露 RAG 问答 API
- 端点：`POST /api/v1/rag/answer`

## API 使用示例

### 请求示例

```bash
curl -X POST http://localhost:8080/api/v1/rag/answer \
  -H "Content-Type: application/json" \
  -d '{
    "query": "如何配置数据源？",
    "conversationId": 123,
    "vectorSimThreshold": 0.3,
    "rerankThreshold": 0.5
  }'
```

### 请求参数说明

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|------|--------|------|
| query | String | 是 | - | 用户问题 |
| conversationId | Long | 否 | -1 | 对话 ID，用于记录低置信问题 |
| vectorSimThreshold | Double | 否 | 0.3 | 向量相似度阈值 |
| rerankThreshold | Double | 否 | 0.5 | 重排阈值 |

### 响应示例

成功响应：
```json
{
  "answer": "根据参考资料 [1] 和 [2]，配置数据源的步骤如下...",
  "refused": false,
  "refusalReason": null
}
```

拒答响应：
```json
{
  "answer": "抱歉，我在知识库中未找到相关信息。",
  "refused": true,
  "refusalReason": "检索结果不足或置信度过低"
}
```

## RAG 流程

1. **混合检索** (HybridSearchService)
   - 向量检索 + 关键词检索
   - RRF 融合排序
   - 返回 top-20 结果

2. **阈值过滤**
   - 过滤向量相似度 < vectorSimThreshold 的结果
   - 如果为空，记录低置信问题并拒答

3. **重排序** (RerankerService)
   - 调用 BGE-Reranker API
   - 返回 top-5 结果

4. **拒答判断**
   - 如果重排结果为空或最高分 < rerankThreshold，拒答

5. **Prompt 构建** (PromptBuilder)
   - 最相关的放开头
   - 次相关的放结尾
   - 包含引用编号

6. **LLM 生成**
   - 调用 Spring AI ChatClient
   - 返回答案

## 低置信问题记录

当遇到以下情况时，会自动记录到 `low_confidence_question` 表：
- 检索无结果或低于阈值 (source: `retrieval_low_conf`)
- 重排分数过低 (source: `retrieval_low_conf`)
- LLM 生成失败 (source: `self_check`)

记录字段：
- `conversation_id`: 对话 ID
- `raw_question`: 原始问题
- `source`: 来源标记
- `reason`: 详细原因
- `created_at`: 创建时间

## 配置要求

需要在 `application.yml` 中配置：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com
```

## 数据库依赖

依赖以下数据库表：
- `documents`: 文档表
- `doc_chunks`: 文档切块表（包含向量）
- `low_confidence_question`: 低置信问题表

## 下一步

Task 6 和 Task 7 的 API 接口应该已经存在。如果需要，可以继续集成或测试这些功能。
