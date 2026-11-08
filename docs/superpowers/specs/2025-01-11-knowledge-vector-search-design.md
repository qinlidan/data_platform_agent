# 知识库向量语义检索系统设计文档

**日期**: 2025-01-11  
**项目**: 数据平台智能助手知识库向量化升级  
**版本**: v1.0

---

## 1. 目标与动机

### 1.1 当前痛点
现有的 `query_faq` 工具采用关键词模糊匹配（`LIKE`）方式检索知识库，存在以下问题：
- **召回精度低**：用户换个说法（如"写文件产生大量小文件如何进行引擎调优"）就无法匹配到对应知识
- **知识管理困难**：无法处理结构化的调优文档和用户手册，缺乏统一的切分和入库流程
- **扩展性差**：新增文档需要手工整理成 FAQ 条目，维护成本高

### 1.2 目标
将知识库检索升级为**向量语义检索**，实现：
1. **语义理解**：通过向量相似度检索，支持同义改写、换说法查询
2. **结构化知识管理**：自动解析 Markdown 文档（调优文档、用户手册），按策略切分成结构化 chunks
3. **高效检索**：利用 pgvector 的 HNSW 索引，实现毫秒级向量检索
4. **幂等与容错**：支持断点续传、重复导入全量覆盖、手工录入去重

---

## 2. 系统架构

### 2.1 技术栈
| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 后端框架 | Spring Boot 3.2.2 + Spring AI 1.0.0 | 已有技术栈 |
| 嵌入模型 | qwen3.7-text-embedding-flash (1024维) | 支持 query/passage 非对称编码 |
| 向量库 | PostgreSQL 12+ + pgvector 0.7+ | HNSW 索引，COSINE 距离 |
| 原文权威源 | MySQL 8+ (已有) | 存储完整 chunk 元数据 |
| ORM | MyBatis-Plus 3.5.5 | 已有技术栈 |
| 前端 | Vue 3 | 已有技术栈 |

### 2.2 双库设计
- **MySQL (`knowledge_chunks`)**：存储完整的 chunk 元数据（questions、answer、symptom_text、section_path 等），作为原文权威源
- **PostgreSQL (`knowledge_vectors`)**：仅存储向量检索必需字段（chunk_id、embedding、doc_type、route_action），支持高性能向量检索

**为什么不用单库？**
1. MySQL 不支持向量检索，pgvector 是 PostgreSQL 专用扩展
2. 向量表只需存储检索必需字段，减小索引大小，提升检索速度
3. 原文权威源与检索层分离，便于后续优化（如切换向量库）

---

## 3. 数据模型

### 3.1 Schema 设计

#### MySQL: `knowledge_chunks` (原文权威源)
```sql
CREATE TABLE knowledge_chunks (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  doc_type           VARCHAR(32)     NOT NULL COMMENT 'spark调优|数据研发平台用户手册',
  doc_id             VARCHAR(128)    NOT NULL COMMENT '文件名（不含扩展名）',
  category           VARCHAR(255)    NOT NULL COMMENT '一级标题',
  scene_type         VARCHAR(32)     NOT NULL COMMENT '二级标题类型枚举',
  scene_title        VARCHAR(64)     NOT NULL COMMENT '二级标题中文名',
  symptom_text       TEXT            NULL COMMENT '现象描述（可空）',
  questions          TEXT            NOT NULL COMMENT '三级标题或问法',
  answer             TEXT            NOT NULL COMMENT '回答正文',
  section_path       VARCHAR(512)    NULL COMMENT '章节路径',
  content_type       VARCHAR(32)     NULL COMMENT 'faq | policy | manual',
  is_tunable         TINYINT(1)      NOT NULL DEFAULT 0,
  route_action       VARCHAR(32)     NULL COMMENT 'tunable | not_tunable | escalate | guide',
  prev_chunk_id      BIGINT UNSIGNED NULL,
  next_chunk_id      BIGINT UNSIGNED NULL,
  vectorize_status   ENUM('pending_vectorization','vectorized') NOT NULL DEFAULT 'pending_vectorization',
  created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_doc_type (doc_type),
  KEY idx_doc_id (doc_id),
  KEY idx_scene_type (doc_type, scene_type),
  KEY idx_vectorize_status (vectorize_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库切块原文表';
```

#### PostgreSQL: `knowledge_vectors` (向量检索)
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_vectors (
  chunk_id     BIGINT        PRIMARY KEY,
  embedding    vector(1024)  NOT NULL,
  doc_type     VARCHAR(32)   NOT NULL,
  route_action VARCHAR(32)   NULL,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_vectors_doc_type ON knowledge_vectors (doc_type);
CREATE INDEX idx_knowledge_vectors_embedding_hnsw ON knowledge_vectors USING hnsw (embedding vector_cosine_ops);
```

### 3.2 向量化文本拼接公式
**目的**：将结构化字段拼接成语义丰富的文本，提升召回精度。

```java
String embeddingText;
if (symptomText != null && !symptomText.isEmpty()) {
    embeddingText = category + " | " + sceneTitle + " | 现象: " + symptomText 
                    + " | 问法: " + questions + " | 说明: " + answer;
} else {
    embeddingText = category + " | " + sceneTitle + " | 问法: " + questions 
                    + " | 说明: " + answer;
}
```

**非对称编码**：
- 文档向量化：添加 `passage:` 前缀（如果模型支持）
- 查询向量化：添加 `query:` 前缀

---

## 4. 文档切分策略管道 (Chunker Pipeline)

### 4.1 设计原则
- **通用性**：不写死章节号，适配任意 Markdown 文档
- **优先级匹配**：按 QA 对 → 表格行 → 标题层级 → 递归兜底 顺序依次尝试
- **结构感知**：保留层级关系（category → scene → sub_scene）
- **质量优先**：块间重叠、句号切分、表头复制
Markdown 文本
  │
  ├─> 1. 按标题层级切分 (产生结构化 blocks)
  │
  ├─> 2. 遍历每个 block，进行【类型路由 (Router)】
  │       │
  │       ├─> [表格 Block] -> 3A. 表格专属切分（按行切，每块复制表头）
  │       │
  │       └─> [文本 Block] -> 3B. 判断是否超长 -> 若是，执行递归切分（大块变小块）
  │
  └─> 4. 统一后处理（针对纯文本块）：
           - 添加相邻块之间的重叠 (Overlap)
           - 裁切到最近的句号 (防止半截话)
  │
  └─> 输出最终 Chunks

### 4.2 切分器实现

#### 1. QA 对切分器（优先级最高）
- **触发条件**：识别 `**Q1：`、`Q:`、`问：` 等强问答特征
- **切分逻辑**：
  - 提取 Q 作为 `questions`
  - 提取 A 作为 `answer`
  - 保留上下文（所属二级/三级标题）填充 `scene_title`、`symptom_text`

#### 2. 表格行切分器
- **触发条件**：遇到 Markdown 表格（`|---|---|`）
- **切分逻辑**：
  - 按行切分，每行作为独立 chunk
  - 将表头列名与每行数据组合，例如：
    ```
    表头：| 名词 | 解释 |
    数据行：| OOM | 内存溢出 |
    生成：questions: "OOM", answer: "名词：OOM，解释：内存溢出"
    ```
  - 每块都复制表头，保证独立可读

#### 3. 标题层级切分器（默认策略）
- **触发条件**：常规 Markdown 文档
- **切分逻辑**：
  - `#` 一级标题 → `category`（如 "Spark/Hive参数调优"）
  - `##` 二级标题 → `scene_type` + `scene_title`（通过关键词映射，见 4.3）
  - `###` 三级标题 → `questions`（如 "1. OOM 内存溢出"）
  - 三级标题下方的正文完整提取为 `answer`

#### 4. 递归兜底切分器
- **触发条件**：单段正文超过 1000 字
- **切分逻辑**：
  - 按句号（`。`）切分
  - 块间保留 10% 重叠（取前一块最后 10% 字符拼接到下一块开头）
  - 确保不留半截话

### 4.3 scene_type 关键词映射表
| 关键词模式 | scene_type | 适用 doc_type |
|-----------|------------|--------------|
| 包含"报错"/"异常"/"错误"/"失败" | `error` | spark调优 |
| 包含"慢"/"性能"/"优化"/"缓慢" | `slow` | spark调优 |
| 包含"pending"/"排队"/"等待"/"未执行" | `pending` | spark调优 |
| 包含"边界"/"限制"/"quota"/"超限" | `boundary` | spark调优 |
| 包含"指南"/"操作"/"使用"/"步骤" | `guide` | 数据研发平台用户手册 |
| 包含"概念"/"原理"/"介绍"/"说明" | `concept` | 数据研发平台用户手册 |
| 包含"FAQ"/"问答"/"常见问题" | `faq` | 数据研发平台用户手册 |
| **默认兜底** | `slow` (spark调优) / `guide` (数据研发平台用户手册) | 根据 doc_type |

---

## 5. 双写落库流程

### 5.1 同步与异步分离架构

**核心设计原则**：
- **同步阶段**：切分 + MySQL 写入（权威原文入库）→ 立即返回给用户
- **异步阶段**：向量化 + pgvector 写入 + MySQL 状态回填 → 后台执行

**优势**：
1. **用户体验**：自动导入、手工录入操作秒级返回，不被向量化阻塞
2. **容错性**：向量化失败不影响原文入库，可通过断点重跑补齐
3. **批量优化**：异步任务可批量处理，提升 API 利用率

### 5.2 完整流程

#### 同步阶段（用户请求线程）
```
1. 切分：Markdown → Chunker Pipeline → List<ChunkDTO>
2. MySQL 批量写入：
   - 事务性批量插入 knowledge_chunks
   - 状态设为 pending_vectorization
   - 获取自增主键列表 List<chunk_id>
3. 提交异步向量化任务：
   - 将 chunk_id 列表提交到向量化线程池
   - 立即返回成功响应给用户
```

#### 异步阶段（后台线程池）
```
4. 批量向量化：
   - 从 MySQL 按 chunk_id 列表查询 embeddingText
   - 调用 qwen3.7-text-embedding-flash（可批量或单条）
   - 返回 1024 维 float[] 向量数组
5. pgvector upsert：
   INSERT INTO knowledge_vectors (chunk_id, embedding, doc_type, route_action)
   VALUES ($1, $2::vector, $3, $4)
   ON CONFLICT (chunk_id) DO UPDATE SET 
     embedding = EXCLUDED.embedding, 
     updated_at = now();
6. MySQL 状态回填：
   UPDATE knowledge_chunks 
   SET vectorize_status = 'vectorized' 
   WHERE id IN (成功向量化的chunk_id列表);
7. 失败重试：
   - 向量化失败的 chunk 记录错误日志
   - 保持 pending_vectorization 状态
   - 由断点重跑机制统一补偿
```

### 5.3 异步任务实现

#### 方案选择
**本期采用 Spring 线程池 + @Async**：
- 轻量级，无需引入消息队列中间件
- 适合中小规模（单次导入 < 1000 chunks）
- 后续可无缝升级到 RabbitMQ / Kafka

#### 代码示例
```java
@Service
@Slf4j
public class VectorizationService {
    
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    
    /**
     * 同步阶段：批量入库并提交异步任务
     */
    @Transactional
    public List<Long> saveChunksSync(List<ChunkDTO> chunks) {
        // 1. 批量写入 MySQL
        List<KnowledgeChunk> entities = chunks.stream()
            .map(this::toEntity)
            .collect(Collectors.toList());
        chunkMapper.insertBatch(entities);
        
        List<Long> chunkIds = entities.stream()
            .map(KnowledgeChunk::getId)
            .collect(Collectors.toList());
        
        log.info("MySQL 同步写入完成: {} chunks", chunkIds.size());
        
        // 2. 提交异步向量化任务
        vectorizeChunksAsync(chunkIds);
        
        return chunkIds;
    }
    
    /**
     * 异步阶段：向量化 + pgvector 写入 + 状态回填
     */
    @Async("vectorizationExecutor")
    public void vectorizeChunksAsync(List<Long> chunkIds) {
        log.info("开始异步向量化: {} chunks", chunkIds.size());
        
        try {
            // 1. 查询 MySQL 获取原文
            List<KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds);
            
            // 2. 批量向量化
            List<Long> successIds = new ArrayList<>();
            for (KnowledgeChunk chunk : chunks) {
                try {
                    String embeddingText = buildEmbeddingText(chunk);
                    float[] embedding = embeddingService.embedDocument(embeddingText);
                    
                    // 3. pgvector upsert
                    vectorSearchService.upsert(
                        chunk.getId(), 
                        embedding, 
                        chunk.getDocType(), 
                        chunk.getRouteAction()
                    );
                    
                    successIds.add(chunk.getId());
                    
                } catch (Exception e) {
                    log.error("向量化失败: chunk_id={}, error={}", 
                        chunk.getId(), e.getMessage());
                }
            }
            
            // 4. 批量回填状态
            if (!successIds.isEmpty()) {
                chunkMapper.updateVectorizeStatus(successIds, "vectorized");
                log.info("异步向量化完成: 成功 {}/{} chunks", 
                    successIds.size(), chunkIds.size());
            }
            
        } catch (Exception e) {
            log.error("异步向量化任务失败: chunkIds={}", chunkIds, e);
        }
    }
    
    private String buildEmbeddingText(KnowledgeChunk chunk) {
        if (chunk.getSymptomText() != null && !chunk.getSymptomText().isEmpty()) {
            return chunk.getCategory() + " | " + chunk.getSceneTitle() 
                + " | 现象: " + chunk.getSymptomText() 
                + " | 问法: " + chunk.getQuestions() 
                + " | 说明: " + chunk.getAnswer();
        } else {
            return chunk.getCategory() + " | " + chunk.getSceneTitle() 
                + " | 问法: " + chunk.getQuestions() 
                + " | 说明: " + chunk.getAnswer();
        }
    }
}

// 线程池配置
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean("vectorizationExecutor")
    public Executor vectorizationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("vectorization-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 5.4 断点重跑与补偿机制

#### 场景 1：应用重启
- **触发时机**：启动时检测到 `pending_vectorization` 状态的 chunks
- **执行逻辑**：
  ```java
  @Component
  public class VectorizationRecoveryTask implements ApplicationListener<ApplicationReadyEvent> {
      
      @Override
      public void onApplicationEvent(ApplicationReadyEvent event) {
          List<Long> pendingIds = chunkMapper.selectList(
              Wrappers.<KnowledgeChunk>lambdaQuery()
                  .eq(KnowledgeChunk::getVectorizeStatus, "pending_vectorization")
          ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
          
          if (!pendingIds.isEmpty()) {
              log.warn("检测到 {} 个待向量化 chunks，启动补偿任务", pendingIds.size());
              vectorizationService.vectorizeChunksAsync(pendingIds);
          }
      }
  }
  ```

#### 场景 2：手动触发补偿
- **HTTP 接口**：
  ```http
  POST /api/knowledge/vectorization/retry
  Request: { "chunkIds": [1, 2, 3] }  // 可选，为空则补偿全部 pending
  Response: { "submitted": 10, "message": "已提交补偿任务" }
  ```

#### 场景 3：定时巡检
- **Cron 任务**（可选）：每小时检查一次 `pending_vectorization` 超过 30 分钟的 chunks
  ```java
  @Scheduled(cron = "0 0 * * * ?")  // 每小时
  public void retryStuckChunks() {
      List<Long> stuckIds = chunkMapper.selectList(
          Wrappers.<KnowledgeChunk>lambdaQuery()
              .eq(KnowledgeChunk::getVectorizeStatus, "pending_vectorization")
              .lt(KnowledgeChunk::getCreatedAt, 
                  LocalDateTime.now().minusMinutes(30))
      ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
      
      if (!stuckIds.isEmpty()) {
          log.warn("检测到 {} 个卡住的 chunks，启动补偿", stuckIds.size());
          vectorizationService.vectorizeChunksAsync(stuckIds);
      }
  }
  ```

### 5.5 幂等保证
- **pgvector 表主键**：`chunk_id` 为主键，`ON CONFLICT DO UPDATE` 自动去重
- **重复提交**：同一 chunk_id 多次向量化，以最后一次写入为准（向量可能略有差异，但不影响检索）
- **并发安全**：
  - MySQL 状态更新幂等（`UPDATE ... WHERE id IN ...`）
  - pgvector upsert 原子性（PostgreSQL 事务保证）
- **验证 SQL**：
  ```sql
  SELECT 
    (SELECT COUNT(*) FROM knowledge_chunks WHERE vectorize_status = 'vectorized') AS mysql_count,
    (SELECT COUNT(DISTINCT chunk_id) FROM knowledge_vectors) AS pg_count;
  -- 两值应相等
  ```

### 5.3 重复导入处理
- **自动导入（启动时）**：按 `doc_id` 全量覆盖
  1. 删除 MySQL 中 `doc_id` 相同的旧 chunks
  2. pgvector 手动删除 `chunk_id IN (已删除的id)`
  3. 重新切分 + 双写
- **手工录入**：按 `(scene_title, questions, answer)` 去重
  1. 查询是否存在相同记录
  2. 存在则跳过，前端标注"已重复"
  3. 不存在则正常入库

---

## 6. 在线检索实现

### 6.1 query_faq 工具升级

#### 入参
```java
@ToolParam(description = "搜索关键词") String keyword
```

#### 出参
```json
{
  "hits": [
    {
      "question": "写文件产生大量小文件如何进行引擎调优",
      "answer": "……",
      "doc_type": "spark调优",
      "route_action": "tunable",
      "is_tunable": true,
      "symptom_text": "作业写文件阶段产生大量小文件，NameNode 压力大、后续读放大",
      "score": 0.86,
      "section_path": "Spark/Hive参数调优 / 作业慢 / 2. 小文件过多"
    }
  ]
}
```

#### 检索流程
1. **向量化查询**：
   ```java
   String queryText = "query: " + keyword;  // 添加 query: 前缀
   float[] queryEmbedding = embeddingModel.embed(queryText);
   ```

2. **pgvector 检索**：
   ```sql
   SELECT chunk_id, doc_type, route_action, 
          1 - (embedding <=> $1::vector) AS score
   FROM knowledge_vectors
   WHERE doc_type = $2  -- 可选：过滤 doc_type
   ORDER BY embedding <=> $1::vector
   LIMIT 5;
   ```

3. **MySQL 补全元数据**：
   ```java
   List<Long> chunkIds = pgvectorResults.stream()
       .map(r -> r.getChunkId())
       .collect(Collectors.toList());
   List<KnowledgeChunk> chunks = chunkService.listByIds(chunkIds);
   ```

4. **组装返回**：保持 pgvector 检索的顺序，补全 MySQL 元数据

### 6.2 doc_type 过滤策略
**本期采用简单方案**：不做前置意图识别，检索时不过滤 doc_type，让相似度决定召回结果。

---

## 7. 前端知识库录入页

### 7.1 功能模块

#### 1. 材料清单与切块预览
- **展示**：列出 `data/query/` 下所有 Markdown 文件（文件名、大小、最后修改时间）
- **操作**：
  - 【重新导入】按钮：触发后端全量覆盖导入
  - 【查看切块】按钮：展示该文档的所有 chunks（可分页）
- **切块预览**：表格展示 chunk 字段（questions、answer、scene_type、scene_title、section_path）

#### 2. 手工录入
- **表单字段**：
  - Markdown 文本框（支持粘贴）
  - doc_type 输入框（用户手动输入文档名）
- **流程**：
  1. 用户粘贴 Markdown，输入 doc_type
  2. 点击【预览切分】按钮 → 后端切分 → 返回 chunks 列表
  3. 前端展示切分结果，**标注哪些与现有记录重复**（通过 `(scene_title, questions, answer)` 查重）
  4. 用户勾选要保留的 chunks，点击【确认入库】
  5. 后端过滤掉重复项，执行双写落库

#### 3. 检索自测
- **表单字段**：
  - 输入框：搜索关键词
  - 下拉框（可选）：doc_type 过滤
- **展示**：
  - 调用 `query_faq` 接口
  - 表格展示召回结果（question、answer、score、doc_type、section_path）
  - 高亮匹配关键词

### 7.2 API 接口设计

#### 1. 获取材料清单
```http
GET /api/knowledge/documents
Response:
[
  {
    "fileName": "spark调优.markdown",
    "docId": "spark调优",
    "docType": "spark调优",
    "fileSize": 7259,
    "lastModified": "2025-01-11T10:30:00",
    "chunkCount": 45
  },
  ...
]
```

#### 2. 重新导入文档
```http
POST /api/knowledge/documents/{docId}/reimport
Response: { "success": true, "message": "已导入 45 个 chunks" }
```

#### 3. 查看文档切块
```http
GET /api/knowledge/documents/{docId}/chunks?page=1&size=20
Response:
{
  "total": 45,
  "chunks": [
    {
      "id": 123,
      "questions": "1. OOM 内存溢出",
      "answer": "...",
      "sceneType": "error",
      "sceneTitle": "作业报错",
      "sectionPath": "Spark/Hive参数调优 / 作业报错 / 1. OOM 内存溢出",
      "vectorizeStatus": "vectorized"
    },
    ...
  ]
}
```

#### 4. 手工录入预览切分
```http
POST /api/knowledge/manual/preview
Request:
{
  "markdownText": "...",
  "docType": "spark调优"
}
Response:
{
  "chunks": [
    {
      "questions": "...",
      "answer": "...",
      "sceneType": "error",
      "sceneTitle": "...",
      "isDuplicate": true,  // 是否与现有记录重复
      "duplicateId": 456    // 重复的 chunk_id（可选）
    },
    ...
  ]
}
```

#### 5. 手工录入确认入库
```http
POST /api/knowledge/manual/import
Request:
{
  "docType": "spark调优",
  "chunks": [
    {
      "questions": "...",
      "answer": "...",
      "sceneType": "error",
      "sceneTitle": "...",
      "symptomText": "..."
    },
    ...
  ]
}
Response: { "success": true, "imported": 3, "skipped": 2 }
```

#### 6. 检索自测
```http
GET /api/knowledge/search/test?keyword=小文件&docType=spark调优
Response:
{
  "hits": [
    {
      "question": "写文件产生大量小文件如何进行引擎调优",
      "answer": "...",
      "doc_type": "spark调优",
      "score": 0.86,
      "section_path": "..."
    },
    ...
  ]
}
```

---

## 8. 启动时自动导入

### 8.1 实现方式
- **监听 ApplicationReadyEvent**：
  ```java
  @Component
  @Slf4j
  public class KnowledgeBaseInitializer implements ApplicationListener<ApplicationReadyEvent> {
      @Override
      public void onApplicationEvent(ApplicationReadyEvent event) {
          log.info("开始自动导入知识库文档");
          knowledgeImportService.autoImportFromDataQuery();
      }
  }
  ```

- **扫描逻辑**：
  ```java
  public void autoImportFromDataQuery() {
      File dir = new File("data/query");
      if (!dir.exists() || !dir.isDirectory()) {
          log.warn("data/query 目录不存在，跳过自动导入");
          return;
      }
      
      File[] files = dir.listFiles((d, name) -> 
          name.endsWith(".markdown") || name.endsWith(".md"));
      for (File file : files) {
          // 文件名去扩展名作为 doc_type
          String docType = file.getName().replaceFirst("\\.[^.]+$", "");
          String docId = docType;  // doc_id = doc_type = 文件名
          importDocument(file, docId, docType);
      }
  }
  ```

### 8.2 全量覆盖逻辑
```java
@Transactional
public void importDocument(File file, String docId, String docType) {
    // 1. 删除旧数据
    List<Long> oldChunkIds = chunkMapper.selectList(
        Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, docId)
    ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
    
    if (!oldChunkIds.isEmpty()) {
        chunkMapper.deleteBatchIds(oldChunkIds);
        vectorRepository.deleteByChunkIdIn(oldChunkIds);  // PostgreSQL 删除
    }
    
    // 2. 解析 + 切分
    String markdown = Files.readString(file.toPath());
    List<ChunkDTO> chunks = chunkerPipeline.process(markdown, docId, docType);
    
    // 3. 双写落库
    for (ChunkDTO chunk : chunks) {
        // 3.1 MySQL 插入
        KnowledgeChunk entity = toEntity(chunk);
        chunkMapper.insert(entity);
        Long chunkId = entity.getId();
        
        // 3.2 向量化
        String embeddingText = buildEmbeddingText(chunk);
        float[] embedding = embeddingModel.embed(embeddingText);
        
        // 3.3 pgvector upsert
        vectorRepository.upsert(chunkId, embedding, docType, chunk.getRouteAction());
        
        // 3.4 回填状态
        entity.setVectorizeStatus("vectorized");
        chunkMapper.updateById(entity);
    }
    
    log.info("导入完成: docType={}, chunks={}", docType, chunks.size());
}
```

---

## 9. 关键技术实现

### 9.1 Spring AI Embedding 集成

#### 配置 DashScope Embedding
```java
@Configuration
public class EmbeddingConfig {
    
    @Bean
    public EmbeddingModel qwenEmbeddingModel(
        @Value("${ai.dashscope.api-key}") String apiKey
    ) {
        // 使用 Spring AI Alibaba 的 DashScope 集成
        DashscopeApi dashscopeApi = new DashscopeApi(apiKey);
        return new DashscopeEmbeddingModel(dashscopeApi, 
            DashscopeEmbeddingOptions.builder()
                .withModel("qwen3.7-text-embedding-flash")
                .build());
    }
}
```

#### 调用 Embedding
```java
@Service
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    
    public float[] embedQuery(String text) {
        String queryText = "query: " + text;  // 添加前缀
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(queryText));
        return toFloatArray(response.getResults().get(0).getOutput());
    }
    
    public float[] embedDocument(String text) {
        String docText = "passage: " + text;  // 可选前缀
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(docText));
        return toFloatArray(response.getResults().get(0).getOutput());
    }
    
    private float[] toFloatArray(List<Double> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).floatValue();
        }
        return array;
    }
}
```

### 9.2 pgvector 集成

#### Spring Data JPA 方式（推荐）
```java
// 1. 添加依赖
// pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.4</version>
</dependency>

// 2. Entity 定义
@Entity
@Table(name = "knowledge_vectors")
public class KnowledgeVector {
    
    @Id
    private Long chunkId;
    
    @Column(columnDefinition = "vector(1024)")
    @Convert(converter = VectorConverter.class)
    private float[] embedding;
    
    private String docType;
    private String routeAction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// 3. Vector Converter
@Converter
public class VectorConverter implements AttributeConverter<float[], PGvector> {
    @Override
    public PGvector convertToDatabaseColumn(float[] attribute) {
        return new PGvector(attribute);
    }
    
    @Override
    public float[] convertToEntityAttribute(PGvector dbData) {
        return dbData.toArray();
    }
}

// 4. Repository
@Repository
public interface KnowledgeVectorRepository extends JpaRepository<KnowledgeVector, Long> {
    
    @Modifying
    @Query(value = """
        INSERT INTO knowledge_vectors (chunk_id, embedding, doc_type, route_action)
        VALUES (:chunkId, :embedding::vector, :docType, :routeAction)
        ON CONFLICT (chunk_id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            updated_at = now()
        """, nativeQuery = true)
    void upsert(@Param("chunkId") Long chunkId,
                @Param("embedding") String embedding,
                @Param("docType") String docType,
                @Param("routeAction") String routeAction);
    
    @Query(value = """
        SELECT chunk_id AS chunkId, doc_type AS docType, route_action AS routeAction,
               1 - (embedding <=> cast(:embedding as vector)) AS score
        FROM knowledge_vectors
        WHERE (:docType IS NULL OR doc_type = :docType)
        ORDER BY embedding <=> cast(:embedding as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<VectorSearchResult> searchSimilar(@Param("embedding") String embedding,
                                            @Param("docType") String docType,
                                            @Param("limit") int limit);
    
    void deleteByChunkIdIn(List<Long> chunkIds);
}

// 5. 搜索结果接口
public interface VectorSearchResult {
    Long getChunkId();
    String getDocType();
    String getRouteAction();
    Double getScore();
}

// 6. Service 封装
@Service
public class VectorSearchService {
    
    private final KnowledgeVectorRepository vectorRepository;
    private final EmbeddingService embeddingService;
    
    public void upsert(Long chunkId, String embeddingText, String docType, String routeAction) {
        float[] embedding = embeddingService.embedDocument(embeddingText);
        String embeddingStr = Arrays.toString(embedding)
            .replace("[", "").replace("]", "");
        vectorRepository.upsert(chunkId, embeddingStr, docType, routeAction);
    }
    
    public List<VectorSearchResult> search(String keyword, String docType, int limit) {
        float[] queryEmbedding = embeddingService.embedQuery(keyword);
        String embeddingStr = Arrays.toString(queryEmbedding)
            .replace("[", "").replace("]", "");
        return vectorRepository.searchSimilar(embeddingStr, docType, limit);
    }
}
```

### 9.3 Chunker Pipeline 实现

#### 接口定义
```java
public interface Chunker {
    /**
     * 判断是否可以处理该文档
     */
    boolean canHandle(String markdown);
    
    /**
     * 切分文档
     */
    List<ChunkDTO> chunk(String markdown, String docId, String docType);
}

@Service
public class ChunkerPipeline {
    
    private final List<Chunker> chunkers;
    
    public ChunkerPipeline() {
        this.chunkers = Arrays.asList(
            new QaPairChunker(),
            new TableRowChunker(),
            new HeadingHierarchyChunker(),
            new RecursiveFallbackChunker()
        );
    }
    
    public List<ChunkDTO> process(String markdown, String docId, String docType) {
        for (Chunker chunker : chunkers) {
            if (chunker.canHandle(markdown)) {
                return chunker.chunk(markdown, docId, docType);
            }
        }
        throw new IllegalArgumentException("No chunker can handle this document");
    }
}
```

#### 标题层级切分器示例
```java
public class HeadingHierarchyChunker implements Chunker {
    
    @Override
    public boolean canHandle(String markdown) {
        return true;  // 默认策略，总是可处理
    }
    
    @Override
    public List<ChunkDTO> chunk(String markdown, String docId, String docType) {
        List<ChunkDTO> chunks = new ArrayList<>();
        
        String[] lines = markdown.split("\n");
        String category = null;
        String sceneTitle = null;
        String sceneType = null;
        String subScene = null;
        StringBuilder answerBuilder = new StringBuilder();
        
        for (String line : lines) {
            if (line.startsWith("# ")) {
                category = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                // 保存上一个 chunk
                if (subScene != null && answerBuilder.length() > 0) {
                    chunks.add(buildChunk(docId, docType, category, sceneType, 
                        sceneTitle, subScene, answerBuilder.toString()));
                    answerBuilder.setLength(0);
                }
                
                sceneTitle = line.substring(3).trim();
                sceneType = inferSceneType(sceneTitle, docType);
            } else if (line.startsWith("### ")) {
                // 保存上一个 chunk
                if (subScene != null && answerBuilder.length() > 0) {
                    chunks.add(buildChunk(docId, docType, category, sceneType, 
                        sceneTitle, subScene, answerBuilder.toString()));
                    answerBuilder.setLength(0);
                }
                
                subScene = line.substring(4).trim();
            } else {
                answerBuilder.append(line).append("\n");
            }
        }
        
        // 保存最后一个 chunk
        if (subScene != null && answerBuilder.length() > 0) {
            chunks.add(buildChunk(docId, docType, category, sceneType, 
                sceneTitle, subScene, answerBuilder.toString()));
        }
        
        return chunks;
    }
    
    private String inferSceneType(String sceneTitle, String docType) {
        if (sceneTitle.contains("报错") || sceneTitle.contains("异常") 
            || sceneTitle.contains("错误") || sceneTitle.contains("失败")) {
            return "error";
        } else if (sceneTitle.contains("慢") || sceneTitle.contains("性能") 
            || sceneTitle.contains("优化") || sceneTitle.contains("缓慢")) {
            return "slow";
        } else if (sceneTitle.contains("pending") || sceneTitle.contains("排队") 
            || sceneTitle.contains("等待") || sceneTitle.contains("未执行")) {
            return "pending";
        } else if (sceneTitle.contains("边界") || sceneTitle.contains("限制") 
            || sceneTitle.contains("quota") || sceneTitle.contains("超限")) {
            return "boundary";
        } else if (sceneTitle.contains("指南") || sceneTitle.contains("操作") 
            || sceneTitle.contains("使用") || sceneTitle.contains("步骤")) {
            return "guide";
        } else if (sceneTitle.contains("概念") || sceneTitle.contains("原理") 
            || sceneTitle.contains("介绍") || sceneTitle.contains("说明")) {
            return "concept";
        } else if (sceneTitle.contains("FAQ") || sceneTitle.contains("问答") 
            || sceneTitle.contains("常见问题")) {
            return "faq";
        } else {
            return "spark调优".equals(docType) ? "slow" : "guide";
        }
    }
    
    private ChunkDTO buildChunk(String docId, String docType, String category, 
                                 String sceneType, String sceneTitle, 
                                 String subScene, String answer) {
        ChunkDTO chunk = new ChunkDTO();
        chunk.setDocId(docId);
        chunk.setDocType(docType);
        chunk.setCategory(category);
        chunk.setSceneType(sceneType);
        chunk.setSceneTitle(sceneTitle);
        chunk.setQuestions(subScene);  // 三级标题作为 questions
        chunk.setAnswer(answer.trim());
        chunk.setSectionPath(category + " / " + sceneTitle + " / " + subScene);
        return chunk;
    }
}
```

---

## 10. 验收标准

### 10.1 功能验收
1. **语义召回**：
   - 查询"写文件产生大量小文件如何进行引擎调优" → 能召回"2. 小文件过多"的 chunk
   - 查询"怎么新建一个定时跑的 SQL 任务" → 能召回用户手册对应章节
   - 查询"找不到某张表" → 能召回相关故障排查内容

2. **断点重跑**：
   - 中断建库任务（kill 进程或手动停止）
   - 重启应用 → 漏向量化的 chunks 自动补齐
   - pgvector 无重复主键（验证：`SELECT chunk_id, COUNT(*) FROM knowledge_vectors GROUP BY chunk_id HAVING COUNT(*) > 1;` 返回空）

3. **手工录入去重**：
   - 粘贴已有 chunk 的 Markdown → 预览时标注"已重复"
   - 确认入库时跳过重复项

### 10.2 性能验收
- **向量检索延迟**：< 100ms（HNSW 索引，Top-5 检索）
- **自动导入时间**：两个文档（共约 20KB）< 30 秒

### 10.3 数据一致性验收
```sql
-- 验证 MySQL 与 pgvector 数据一致性
SELECT 
  (SELECT COUNT(*) FROM knowledge_chunks WHERE vectorize_status = 'vectorized') AS mysql_vectorized_count,
  (SELECT COUNT(DISTINCT chunk_id) FROM knowledge_vectors) AS pg_unique_chunk_count;
-- 两个值应相等
```

---

## 11. 非功能需求

### 11.1 不做的事（Non-Goals）
1. ❌ 关键词召回、混合检索、重排（只跑 dense 向量单路）
2. ❌ 多模型对比（只用 qwen3.7-text-embedding-flash）
3. ❌ 向量索引优化实验（HNSW 参数调优）
4. ❌ 前置意图识别（简单场景，不过滤 doc_type）

### 11.2 反目标（Anti-Goals）
1. ❌ **不允许**向量化失败导致 MySQL 数据不一致（双写必须幂等）
2. ❌ **不允许**切分器写死章节号（必须通用化）
3. ❌ **不允许**pgvector 无主键约束（重跑会产生重复行）

---

## 12. 风险与依赖

### 12.1 技术风险
| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| PostgreSQL 未安装 pgvector 扩展 | 无法运行 | 启动时检查扩展，提供安装文档 |
| qwen API 限流 | 向量化失败 | 批量请求时加延时，记录失败 chunk_id 供重试 |
| Markdown 解析错误 | 切分质量差 | 提供切块预览功能，人工审核 |

### 12.2 依赖项
1. **外部服务**：
   - qwen3.7-text-embedding-flash API（通过 Spring AI Alibaba 调用）
   - PostgreSQL 12+ with pgvector 0.7+ (Docker 已拉取)

2. **内部模块**：
   - 现有的 FaqService（废弃，替换为 KnowledgeService）
   - 现有的 AiAssistantController（需更新 SYSTEM_PROMPT）

---

## 13. 实施决策

### 13.1 已确定
- ✅ doc_type = 文件名（去扩展名）
- ✅ scene_type 通过关键词简单映射
- ✅ PostgreSQL + pgvector 已通过 Docker 部署
- ✅ 废弃现有 faq 表数据

### 13.2 我的实现决策
以下是我在实现时会做的技术选择，你可以随时调整：

1. **PostgreSQL 配置方式**：使用 Spring Data JPA + 原生查询（不引入 Spring AI VectorStore 抽象，保持灵活）
2. **向量字符串格式**：float[] → `"0.1,0.2,0.3,..."` → PostgreSQL `::vector` 转换
3. **Markdown 解析**：使用正则表达式（不引入 commonmark-java 库，保持轻量）
4. **批量向量化**：单条请求（不做批量优化，确保简单可靠）
5. **断点重跑触发**：提供 HTTP 接口手动触发（不做自动检测）

---

## 14. 后续优化方向

### 14.1 短期优化（1-2 个月内）
1. **混合检索**：结合 BM25 关键词召回 + 向量检索，融合排序
2. **重排模型**：引入 reranker 模型（如 bge-reranker），提升 Top-K 精度
3. **前置意图识别**：通过 LLM 判断查询意图，动态过滤 doc_type

### 14.2 长期优化（3-6 个月内）
1. **多模态检索**：支持图片、表格的向量化与检索
2. **用户反馈闭环**：记录召回结果的点击率，优化 Embedding 策略
3. **增量更新**：支持修改单个 chunk 而非全量覆盖

---

## 15. 附录

### 15.1 参考文档
- [Spring AI 1.0.0 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba DashScope 集成](https://sca.aliyun.com/docs/2023/user-guide/ai/dashscope/)
- [pgvector 官方文档](https://github.com/pgvector/pgvector)
- [pgvector-java 库](https://github.com/pgvector/pgvector-java)

### 15.2 关键配置示例
```yaml
# application.yml

spring:
  # MySQL 配置（已有）
  datasource:
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  # PostgreSQL 配置（新增）
  datasource-pgvector:
    url: jdbc:postgresql://localhost:5432/postgres?currentSchema=public
    username: postgres
    password: ${POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: none  # 手动建表
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      # embedding 模型会自动使用 qwen3.7-text-embedding-flash
```

### 15.3 pgvector 初始化脚本
```sql
-- 在 PostgreSQL 中执行
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_vectors (
  chunk_id     BIGINT        PRIMARY KEY,
  embedding    vector(1024)  NOT NULL,
  doc_type     VARCHAR(32)   NOT NULL,
  route_action VARCHAR(32)   NULL,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 普通索引
CREATE INDEX idx_knowledge_vectors_doc_type ON knowledge_vectors (doc_type);

-- HNSW 向量索引（可能需要几分钟）
CREATE INDEX idx_knowledge_vectors_embedding_hnsw 
ON knowledge_vectors USING hnsw (embedding vector_cosine_ops);
```

---

**文档结束**
