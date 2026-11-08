# 混合检索 + 重排 + 评估体系设计文档

**日期**: 2026-11-04  
**状态**: Draft  
**技术栈**: Spring Boot 3 / Spring AI / MyBatis / 阿里云 DashScope Java SDK

---

## 1. 设计意图

### 1.1 目标
将知识库检索从单一向量检索升级为混合检索（向量 + 关键词）+ 重排的完整链路，并建立评估体系来量化检索质量提升。

### 1.2 为什么现在做
当前系统仅依赖向量检索，对带具体型号、专有名词的查询召回不足。混合检索通过关键词路补足精确匹配能力，重排进一步提升相关性排序，评估体系则让优化变得可度量。

### 1.3 成功标准
1. 四策略对比评估（纯向量/纯关键词/混合/混合+重排）能跑出 Recall@K 和 MRR 指标
2. 查询"Spark 3.2 OOM"能通过关键词路命中带"3.2"的文档
3. 回答带引用编号，点击可溯源到 section_path
4. 问知识库外问题时明确拒答，并记录到 low_confidence_questions 表

---

## 2. 架构原则

### 2.1 数据源职责划分
- **MySQL（knowledge_chunks）**: 原文权威源，负责审计、回滚、完整原文查看，存储 `doc_type`, `scene_title`, `questions`, `answer` 等原始字段
- **PostgreSQL（knowledge_vectors）**: 检索工作副本，包含：
  - `text` 字段：`doc_type + scene_title + questions + answer`（用于全文检索）
  - `rerank_text` 字段：`scene_title + questions + answer`（用于重排模型输入）
  - `section_path` 字段：用于引用溯源展示

**关键设计决策**：
- 检索、RRF 融合、重排、引用展示**完全在 pgvector 内完成**，不跨库回 MySQL
- MySQL 仅在用户点击"查看完整原文"时按 `chunk_id` 回查一次
- 冗余字段是**工作副本**，任何原文修订仍以 MySQL 为准

### 2.2 字段组合说明
**全文检索字段 (`text`)**：
- 用途：关键词检索（tsvector 全文索引）
- 来源：`doc_type + " " + scene_title + " " + questions + " " + answer`
- 理由：包含 `doc_type` 可提高文档类型过滤的全文检索召回率

**精排字段 (`rerank_text`)**：
- 用途：重排模型输入（传给 DashScope gte-rerank）
- 来源：`scene_title + " " + questions + " " + answer`
- 理由：不包含 `doc_type` 避免类型标签干扰语义相关性计算

### 2.3 双写一致性（风险项）
`text`, `rerank_text` 和 `section_path` 在两库间必须保持同步：

**现有同步机制**（待确认）：
- 导入时：`KnowledgeImportService` 先写 MySQL，再调用 `VectorizationService` 异步向量化并写 pgvector
- 更新时：重新导入会先删除旧数据，再插入新数据

**需要补强**：
1. `VectorizationService.vectorizeChunk()` 在写入 embedding 时，必须同时构造并写入 `text`, `rerank_text` 和 `section_path`
2. `section_path` 可能因目录结构调整而变化，需要显式同步逻辑
3. 双写失败时的补偿机制：定时对账任务或重试队列

**实现计划中明确**：
- 在向量化方法中增加字段组合逻辑：
  - `text = doc_type + " " + scene_title + " " + questions + " " + answer`
  - `rerank_text = scene_title + " " + questions + " " + answer`
- 添加对账 SQL 检查两库 chunk_id 对应数据的一致性

---

## 3. Schema 变更

### 3.1 PostgreSQL knowledge_vectors 表
```sql
-- 新增字段（最小冗余集）
ALTER TABLE knowledge_vectors ADD COLUMN text TEXT;          -- 全文检索字段（doc_type + scene_title + questions + answer）
ALTER TABLE knowledge_vectors ADD COLUMN section_path TEXT;  -- 章节路径

-- 新增精排字段（用于重排模型输入）
ALTER TABLE knowledge_vectors ADD COLUMN rerank_text TEXT;   -- 精排字段（scene_title + questions + answer，不含 doc_type）

-- 中文全文检索：tsvector 生成列
ALTER TABLE knowledge_vectors
ADD COLUMN text_tsv tsvector
GENERATED ALWAYS AS (to_tsvector('jiebacfg', coalesce(text, ''))) STORED;

-- 索引
CREATE INDEX idx_kv_tsv ON knowledge_vectors USING GIN (text_tsv);
CREATE INDEX idx_kv_embedding ON knowledge_vectors 
USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
```

**中文分词方案**：
- **优先**：`pg_jieba`（需要在 `shared_preload_libraries` 中预加载并重启 PostgreSQL）
- **备选**：`zhparser`（无需预加载，直接 `CREATE EXTENSION zhparser` 后手动创建 text search configuration）
- **两者都装不上时停止，不降级到 n-gram**

### 3.2 MySQL low_confidence_questions 表
```sql
-- 确保中文 ENUM 定义值/DEFAULT/COMMENT 按 utf8mb4 解析
SET NAMES utf8mb4;

CREATE TABLE low_confidence_questions (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT UNSIGNED NULL                    COMMENT '来源会话',
  raw_question    TEXT            NOT NULL                COMMENT '用户原话,带情绪口语',
  source          ENUM('retrieval_low_conf','self_check','user_feedback') NOT NULL COMMENT '入池入口:检索证据低 / 生成自评不足 / 用户反馈未解决',
  reason          TEXT            NULL                    COMMENT '判不能的原因,留作复盘',
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入池时间',
  PRIMARY KEY (id),
  KEY idx_source (source),
  KEY idx_created_at (created_at),
  CONSTRAINT fk_lcq_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低置信度问题池';
```
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created (created_at),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低置信度问题池';
```

---

## 4. 混合检索设计

### 4.1 RRF 融合 SQL
两路检索（向量 + 关键词）在 PostgreSQL 内通过 `FULL OUTER JOIN` 完成 RRF 融合：

```sql
WITH vector_search AS (
    SELECT chunk_id,
           text,
           section_path,
           doc_type,
           row_number() OVER (ORDER BY embedding <=> CAST(:queryVector AS vector)) AS v_rank
    FROM knowledge_vectors
    WHERE (:docType IS NULL OR doc_type = :docType)
    ORDER BY embedding <=> CAST(:queryVector AS vector)
    LIMIT 50
),
keyword_search AS (
    SELECT chunk_id,
           text,
           section_path,
           doc_type,
           row_number() OVER (ORDER BY ts_rank_cd(text_tsv, to_tsquery('jiebacfg', :queryText)) DESC) AS k_rank
    FROM knowledge_vectors
    WHERE text_tsv @@ to_tsquery('jiebacfg', :queryText)
      AND (:docType IS NULL OR doc_type = :docType)
    ORDER BY ts_rank_cd(text_tsv, to_tsquery('jiebacfg', :queryText)) DESC
    LIMIT 50
)
SELECT 
    coalesce(v.chunk_id, k.chunk_id) AS chunk_id,
    coalesce(v.text, k.text) AS text,
    coalesce(v.section_path, k.section_path) AS section_path,
    coalesce(v.doc_type, k.doc_type) AS doc_type,
    coalesce(1.0 / (60 + v.v_rank), 0.0) + coalesce(1.0 / (60 + k.k_rank), 0.0) AS rrf_score,
    1 - (v.embedding <=> CAST(:queryVector AS vector)) AS vector_similarity
FROM vector_search v
FULL OUTER JOIN keyword_search k USING (chunk_id)
ORDER BY rrf_score DESC
LIMIT :topK;
```

**参数说明**：
- `:queryVector`: float[] 查询向量（通过 Spring AI Embedding 生成）
- `:queryText`: 中文查询文本（经 jieba 分词后的查询串）
- `:docType`: 文档类型过滤（可选）
- `:topK`: 融合后返回的候选数量（默认 30）
- `k=60`: RRF 平滑常数

**关于 BM25**：
PostgreSQL 的 `ts_rank_cd` 不是严格 BM25（缺少 IDF 加权）。如果需要真正的 BM25，有两种方案：
- **(a) 默认**：用 GIN 索引收窄候选后，在应用层用标准 BM25 公式重打分
- **(b) 可选**：引入 ParadeDB 的 `pg_search` 扩展（需要确认是否能安装）

**本设计默认使用 (a) 方案**。

### 4.2 MyBatis 映射
```java
// VectorSearchRepository.java（新增接口）
public interface VectorSearchRepository {
    
    @Select("""
        WITH vector_search AS (
            SELECT chunk_id, text, section_path, doc_type,
                   row_number() OVER (ORDER BY embedding <=> CAST(#{queryVector} AS vector)) AS v_rank
            FROM knowledge_vectors
            WHERE (#{docType} IS NULL OR doc_type = #{docType})
            ORDER BY embedding <=> CAST(#{queryVector} AS vector)
            LIMIT 50
        ),
        keyword_search AS (
            SELECT chunk_id, text, section_path, doc_type,
                   row_number() OVER (ORDER BY ts_rank_cd(text_tsv, to_tsquery('jiebacfg', #{queryText})) DESC) AS k_rank
            FROM knowledge_vectors
            WHERE text_tsv @@ to_tsquery('jiebacfg', #{queryText})
              AND (#{docType} IS NULL OR doc_type = #{docType})
            ORDER BY ts_rank_cd(text_tsv, to_tsquery('jiebacfg', #{queryText})) DESC
            LIMIT 50
        )
        SELECT 
            coalesce(v.chunk_id, k.chunk_id) AS chunkId,
            coalesce(v.text, k.text) AS text,
            coalesce(v.section_path, k.section_path) AS sectionPath,
            coalesce(v.doc_type, k.doc_type) AS docType,
            coalesce(1.0 / (60 + v.v_rank), 0.0) + coalesce(1.0 / (60 + k.k_rank), 0.0) AS rrfScore,
            1 - (embedding <=> CAST(#{queryVector} AS vector)) AS vectorSimilarity
        FROM vector_search v
        FULL OUTER JOIN keyword_search k USING (chunk_id)
        ORDER BY rrfScore DESC
        LIMIT #{topK}
        """)
    List<HybridSearchResult> hybridSearch(
        @Param("queryVector") String queryVector,  // pgvector 格式：[0.1,0.2,...]
        @Param("queryText") String queryText,
        @Param("docType") String docType,
        @Param("topK") int topK
    );
}
```

**DTO**:
```java
@Data
public class HybridSearchResult {
    private Long chunkId;
    private String text;
    private String sectionPath;
    private String docType;
    private Double rrfScore;
    private Double vectorSimilarity;  // 用于拒答阈值判断
}
```

---

## 5. 重排设计

### 5.1 阿里云 DashScope Rerank API
**模型**: `qwen3.7-text-rerank`（阿里云 DashScope 的重排模型）  
**端点**: `https://ws-0qvl6bswjpub8oz9.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank'`

**请求体**:
```json
{
  "model": "gte-rerank",
  "query": "用户查询文本",
  "documents": [
    {"text": "chunk 1 原文"},
    {"text": "chunk 2 原文"}
  ]
}
```

**响应体**:
```json
{
  "results": [
    {"index": 0, "relevance_score": 0.95},
    {"index": 1, "relevance_score": 0.82}
  ]
}
```

### 5.2 Java SDK 调用
```java
@Service
public class RerankService {
    
    @Value("${dashscope.api-key}")
    private String apiKey;
    
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topN) {
        // 1. 构造请求
        List<Map<String, String>> documents = candidates.stream()
            .map(c -> Map.of("text", c.getText()))
            .toList();
        
        Map<String, Object> request = Map.of(
            "model", "gte-rerank",
            "query", query,
            "documents", documents,
            "top_n", topN
        );
        
        // 2. 调用 DashScope SDK
        RerankResponse response = dashscopeClient.rerank(request);
        
        // 3. 按 relevance_score 排序并映射回原始 chunk
        return response.getResults().stream()
            .map(r -> {
                HybridSearchResult original = candidates.get(r.getIndex());
                return new RerankResult(
                    original.getChunkId(),
                    original.getText(),
                    original.getSectionPath(),
                    r.getRelevanceScore()
                );
            })
            .toList();
    }
}
```

### 5.3 Prompt 组装："首尾相关"布局
为避免 Lost in the Middle，重排后将最相关的 chunk 放在 prompt 的开头和结尾：

```java
public String buildPromptWithCitations(String query, List<RerankResult> reranked) {
    if (reranked.isEmpty()) {
        return "无相关知识";
    }
    
    // 最相关的放开头
    StringBuilder prompt = new StringBuilder();
    prompt.append("[参考资料 1]\n");
    prompt.append(reranked.get(0).getText()).append("\n");
    prompt.append("来源: ").append(reranked.get(0).getSectionPath()).append("\n\n");
    
    // 中间的按顺序
    for (int i = 1; i < reranked.size() - 1; i++) {
        RerankResult r = reranked.get(i);
        prompt.append("[参考资料 ").append(i + 1).append("]\n");
        prompt.append(r.getText()).append("\n");
        prompt.append("来源: ").append(r.getSectionPath()).append("\n\n");
    }
    
    // 次相关的放结尾（如果有多个）
    if (reranked.size() > 1) {
        RerankResult last = reranked.get(reranked.size() - 1);
        prompt.append("[参考资料 ").append(reranked.size()).append("]\n");
        prompt.append(last.getText()).append("\n");
        prompt.append("来源: ").append(last.getSectionPath()).append("\n\n");
    }
    
    prompt.append("用户问题: ").append(query).append("\n");
    prompt.append("请基于以上参考资料回答，并在回答中标注引用编号 [1][2] 等。");
    
    return prompt.toString();
}
```

---

## 6. 生成质量控制

### 6.1 拒答阈值
**RRF 分数不能作为拒答依据**（它只编码排名），应使用：
- **向量相似度阈值**: `vectorSimilarity < 0.6` 拒答
- **重排分数阈值**: `relevanceScore < 0.5` 拒答

阈值在评估集上校准后写入配置。

### 6.2 模型自评 + 低置信问题池
```java
public String generateAnswer(String query, List<RerankResult> reranked) {
    String prompt = buildPromptWithCitations(query, reranked);
    
    // 1. 先让模型判断能否回答
    String systemPrompt = """
        你是一个智能助手。如果参考资料不足以回答用户问题，请直接回复"抱歉，知识库中没有相关信息"。
        禁止编造、猜测或承诺具体到账时间等。
        """;
    
    String response = llmClient.chat(systemPrompt, prompt);
    
    // 2. 检测拒答关键词
    if (response.contains("没有相关信息") || response.contains("无法回答")) {
        // 记录到低置信问题池
        lowConfidenceQuestionMapper.insert(LowConfidenceQuestion.builder()
            .question(query)
            .sessionId(getCurrentSessionId())
            .source("chat")
            .reason("model_refuse")
            .build());
    }
    
    return response;
}
```

### 6.3 引用编号溯源
- 回答中的 `[1][2]` 对应 `reranked` 列表索引
- 前端点击引用时，从 `sectionPath` 字段获取章节路径展示
- 点击"查看完整原文"时，才按 `chunkId` 回查 MySQL

---

## 7. Query 理解

### 7.1 改写归一（检索前）
```java
public String normalizeQuery(String rawQuery) {
    String systemPrompt = """
        将口语化的模糊问法改写为标准问法。
        示例：
        输入: "spark 咋这么慢"
        输出: "Spark 性能慢"
        """;
    return llmClient.chat(systemPrompt, rawQuery);
}
```

### 7.2 同义词扩展（仅检索侧）
关键词路将原词与同义词用 `|` (OR) 拼接：
```java
public String expandSynonyms(String query) {
    // 示例: "内存溢出" -> "内存溢出 | OOM | OutOfMemoryError"
    Map<String, String> synonyms = Map.of(
        "内存溢出", "内存溢出 | OOM | OutOfMemoryError",
        "性能慢", "性能慢 | 卡顿 | 延迟高"
    );
    
    for (Map.Entry<String, String> entry : synonyms.entrySet()) {
        if (query.contains(entry.getKey())) {
            query = query.replace(entry.getKey(), entry.getValue());
        }
    }
    
    return query;
}
```

**不在入库侧拆存多份**，避免数据膨胀和引用溯源混乱。

---

## 8. 评估体系

### 8.1 评估集设计
```java
@Data
public class EvaluationCase {
    private String query;           // 查询问题
    private String difficulty;      // 难度: easy | medium | hard
    private String queryType;       // 类型: keyword | semantic | hybrid
    private List<Long> groundTruth; // 标注的正确 chunk_id 列表
}
```

**难度梯度**:
- **Easy**: 精确匹配型，如"Spark 3.2 OOM 如何处理"
- **Medium**: 语义理解型，如"任务运行很慢怎么办"
- **Hard**: 多跳推理型，如"Join 导致数据倾斜的解决方案"

### 8.2 评估指标
```java
public class RetrievalMetrics {
    
    // Recall@K: 正确文档被召回的比例
    public double recallAtK(List<Long> retrieved, List<Long> groundTruth, int k) {
        Set<Long> topK = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        long hits = groundTruth.stream().filter(topK::contains).count();
        return (double) hits / groundTruth.size();
    }
    
    // MRR: Mean Reciprocal Rank
    public double mrr(List<Long> retrieved, List<Long> groundTruth) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (groundTruth.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }
}
```

### 8.3 四策略对比
```java
public void runEvaluation(List<EvaluationCase> cases) {
    for (EvaluationCase c : cases) {
        // 策略1: 纯向量
        List<Long> vectorOnly = vectorSearch(c.getQuery(), 10);
        
        // 策略2: 纯关键词
        List<Long> keywordOnly = keywordSearch(c.getQuery(), 10);
        
        // 策略3: 混合 (RRF)
        List<Long> hybrid = hybridSearch(c.getQuery(), 10);
        
        // 策略4: 混合 + Rerank
        List<Long> hybridRerank = hybridSearchWithRerank(c.getQuery(), 10);
        
        // 计算指标
        System.out.printf("Query: %s (Type: %s, Difficulty: %s)%n", 
            c.getQuery(), c.getQueryType(), c.getDifficulty());
        System.out.printf("  Vector: R@5=%.2f, MRR=%.2f%n",
            recallAtK(vectorOnly, c.getGroundTruth(), 5),
            mrr(vectorOnly, c.getGroundTruth()));
        // ... 其他策略
    }
}
```

评估报告按 `queryType` 分桶输出，便于分析各策略的适用场景。

---

## 9. 前端配套

### 9.1 引用展示
**需求**: 点击引用编号 `[1]` 弹窗显示来源原文 + 章节路径

**后端接口**:
```java
@GetMapping("/api/chat/citation/{citationIndex}")
public CitationDetail getCitation(@PathVariable int citationIndex, @RequestParam String sessionId) {
    // 从会话上下文获取当前回答对应的 reranked 列表
    List<RerankResult> reranked = sessionContext.getRerankedResults(sessionId);
    RerankResult citation = reranked.get(citationIndex - 1);  // [1] 对应索引 0
    
    return CitationDetail.builder()
        .text(citation.getText())
        .sectionPath(citation.getSectionPath())
        .chunkId(citation.getChunkId())
        .build();
}
```

**前端实现**:
- 正则匹配回答中的 `[1][2]` 转为可点击链接
- 点击时调用上述接口，弹窗展示 `text` 和 `sectionPath`
- 弹窗底部提供"查看完整原文"按钮，点击时按 `chunkId` 回查 MySQL

### 9.2 反馈采集
**需求**: 每段回答下方 👍 / 👎 按钮，点击后锁定

**实现**: 纯前端 state 管理，为后续数据飞轮预留入口：
```javascript
const feedback = ref(null); // null | 'thumbs-up' | 'thumbs-down'

const handleFeedback = (type) => {
  if (feedback.value === null) {
    feedback.value = type;
    // 可选：发送到后端记录
    axios.post('/api/chat/feedback', {
      sessionId: currentSessionId,
      messageId: currentMessageId,
      feedback: type
    });
  }
};
```

---

## 10. 实施风险

### 10.1 双写一致性（高风险）
**风险**: `text` 和 `section_path` 在 MySQL 更新后未同步到 pgvector

**缓解措施**:
1. 在 `VectorizationService` 中显式同步这两个字段
2. 添加定时对账任务：
   ```sql
   SELECT m.id, m.answer, m.section_path, v.text, v.section_path
   FROM knowledge_chunks m
   LEFT JOIN knowledge_vectors v ON m.id = v.chunk_id
   WHERE m.answer != v.text OR m.section_path != v.section_path;
   ```
3. 对账发现不一致时触发补偿同步

### 10.2 中文分词扩展安装（中风险）
**风险**: `pg_jieba` 需要预加载并重启 PostgreSQL，托管数据库可能不支持

**缓解措施**:
1. 先尝试 `CREATE EXTENSION pg_jieba`
2. 如失败，退到 `zhparser`（无需预加载）
3. 两者都装不上时，**停止并上报**，不降级到 n-gram

### 10.3 RRF 查询性能（中风险）
**风险**: `FULL OUTER JOIN` 的执行计划未走 GIN/HNSW 索引

**缓解措施**:
1. 用 `EXPLAIN ANALYZE` 验证索引使用情况
2. 如性能不达标，将 CTE 改为应用层合并
3. 调整 `LIMIT 50` 的候选数量

---

## 11. 非目标（本章不做）

1. **指代消解**: "它是什么意思"中的"它"不做上下文跟踪
2. **多轮改写**: 不基于历史对话改写当前查询
3. **实时反馈闭环**: 👍/👎 数据暂不用于模型微调或检索优化

---

## 12. 实施计划（概要）

1. **PostgreSQL Schema 变更** (1d)
   - 添加 `text`, `section_path`, `text_tsv` 字段
   - 安装中文分词扩展（`pg_jieba` 或 `zhparser`）
   - 创建 GIN 索引

2. **双写逻辑补强** (2d)
   - 修改 `VectorizationService` 同步 text 和 section_path
   - 实现对账任务

3. **混合检索实现** (3d)
   - MyBatis 映射 RRF 融合 SQL
   - Spring AI Embedding 生成查询向量
   - 单元测试验证索引使用

4. **重排集成** (2d)
   - 阿里云 DashScope Java SDK 调用
   - Prompt 首尾布局
   - 拒答阈值校准

5. **评估体系** (3d)
   - 构造评估集（30+ cases）
   - 四策略对比脚本
   - 分桶报告生成

6. **前端配套** (2d)
   - 引用点击展示
   - 反馈按钮

7. **文档与验收** (1d)
   - 部署文档
   - 演示命令
   - 验收测试

**总计**: 约 14 个工作日

---

## 13. 验收测试用例

### 13.1 关键词路召回
```
输入: "Spark 3.2 内存溢出"
预期: 召回文档中包含"3.2"和"OOM"关键词的 chunk
验证: 检查 keyword_search CTE 的结果
```

### 13.2 引用溯源
```
输入: "任务慢怎么办"
预期: 回答包含 [1][2] 引用编号
操作: 点击 [1]，弹窗显示原文和 section_path
验证: section_path 非空，text 与 MySQL 一致
```

### 13.3 拒答机制
```
输入: "明天天气怎么样"
预期: 回复"知识库中没有相关信息"
验证: low_confidence_questions 表新增一条记录
```

### 13.4 四策略对比
```
运行评估脚本，输出示例：
Query Type: keyword
  Vector:         R@5=0.40, MRR=0.25
  Keyword:        R@5=0.80, MRR=0.67
  Hybrid:         R@5=0.85, MRR=0.75
  Hybrid+Rerank:  R@5=0.90, MRR=0.83
```

---

## 附录A：技术选型对照

| 需求文档描述            | 实际技术栈                          |
| ----------------------- | ----------------------------------- |
| FastAPI                 | Spring Boot 3                       |
| SQLAlchemy              | MyBatis + Spring Data JPA           |
| LangChain               | Spring AI                           |
| Python asyncio          | Spring @Async                       |
| qwen3.7-text-rerank     | gte-rerank (阿里云 DashScope)       |
| Python numpy array      | Java float[]                        |

---

**文档状态**: 待审核  
**下一步**: 等待审核通过后，进入实施计划阶段（superpowers:writing-plans）
