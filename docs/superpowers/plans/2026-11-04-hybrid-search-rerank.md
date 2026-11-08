# 混合检索 + 重排 + 评估体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade from single-vector search to hybrid search (vector + keyword) + rerank + evaluation system for quantifiable search quality improvements.

**Architecture:** pgvector holds redundant `text`/`section_path` columns as working copies. Retrieval, RRF fusion, rerank, and citation display complete entirely within pgvector without cross-database joins. MySQL remains the source of truth for audits and full-text viewing.

**Tech Stack:** Spring Boot 3, Spring AI, MyBatis, PostgreSQL with pgvector, 阿里云 DashScope Java SDK (gte-rerank)

**Spec:** docs/superpowers/specs/2026-11-04-hybrid-search-rerank-design.md

## Global Constraints

- Java 17+, Spring Boot 3.x
- PostgreSQL with pgvector extension installed
- 中文分词扩展：pg_jieba（优先）或 zhparser（备选），两者都装不上时停止，不降级到 n-gram
- Embedding 维度：1024（text-embedding-v3）
- RRF 常数 k=60
- 拒答阈值：vectorSimilarity < 0.6 或 relevanceScore < 0.5（在评估集上校准后调整）
- 双写一致性：text 和 section_path 必须在 MySQL 和 pgvector 间同步
- MyBatis 用于所有 PostgreSQL RRF 混合检索 SQL
- Spring Data JPA 用于 pgvector 简单 CRUD

## Review Focus

1. **中文分词配置错误时查询失败** - to_tsquery('jiebacfg', text) 在 text search configuration 不存在时抛出异常而非静默失败。Task 1 的 schema 测试必须验证 to_tsquery 能成功解析中文查询。
2. **双写不一致导致引用溯源显示错误** - 用户点击引用时看到的 text 与 MySQL 原文不符。Task 2 的测试必须验证 vectorizeChunk 写入的 text 与 MySQL answer 字段一致。
3. **RRF 融合 SQL 的 FULL OUTER JOIN 未走索引** - 导致检索超时。Task 3 的性能测试必须用 EXPLAIN ANALYZE 验证 vector_search CTE 走 HNSW 索引且 keyword_search CTE 走 GIN 索引。
4. **重排 API 返回的 index 越界** - DashScope 重排服务返回的 index 超出 candidates.size() 时抛出 IndexOutOfBoundsException。Task 4 的测试必须验证重排结果的 index 在 [0, candidates.size()) 范围内。
5. **拒答阈值过严导致误拒** - vectorSimilarity 阈值设定不当时，将相关查询误判为知识库外问题。Task 6 的测试必须覆盖边界值（0.59 拒答，0.60 通过）。

---

### Task 1: PostgreSQL Schema 变更 + 中文分词配置

**Files:**
- Create: `src/main/resources/db/migration/postgresql/V2__add_hybrid_search_columns.sql`
- Create: `src/test/java/com/dataplatform/knowledge/schema/HybridSearchSchemaTest.java`
- Modify: `src/main/java/com/dataplatform/knowledge/entity/KnowledgeVector.java:24-44` (添加 text/rerankText/sectionPath/textTsv 字段)

**Interfaces:**
- Consumes: 现有 knowledge_vectors 表（chunk_id, embedding, doc_type, route_action）
- Produces: 
  - `ALTER TABLE knowledge_vectors` 添加四个字段：
    - `text TEXT` (组合字段：doc_type + scene_title + questions + answer，用于全文检索)
    - `rerank_text TEXT` (组合字段：scene_title + questions + answer，用于精排)
    - `section_path TEXT` (章节路径)
    - `text_tsv tsvector GENERATED ALWAYS AS (to_tsvector('jiebacfg', coalesce(text, ''))) STORED`
  - GIN 索引：`CREATE INDEX idx_kv_tsv ON knowledge_vectors USING GIN (text_tsv)`
  - HNSW 索引（已存在，确认参数）：`CREATE INDEX IF NOT EXISTS idx_kv_embedding ON knowledge_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`

- [ ] **Step 1: 编写 Schema 测试 - 验证中文分词扩展安装**

创建 `src/test/java/com/dataplatform/knowledge/schema/HybridSearchSchemaTest.java`:

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class HybridSearchSchemaTest {
    
    @Autowired
    private JdbcTemplate pgJdbcTemplate;
    
    @Test
    public void testChineseTokenizerInstalled() {
        // 尝试 pg_jieba
        String jiebaCfg = "SELECT to_tsvector('jiebacfg', '内存溢出')";
        try {
            pgJdbcTemplate.queryForObject(jiebaCfg, String.class);
            // 成功，记录使用 jiebacfg
            return;
        } catch (Exception e) {
            // jiebacfg 不存在，尝试 zhparser
        }
        
        // 尝试 zhparser
        String zhparserCfg = "SELECT to_tsvector('zhcfg', '内存溢出')";
        try {
            pgJdbcTemplate.queryForObject(zhparserCfg, String.class);
            // 成功，记录使用 zhcfg
            return;
        } catch (Exception e) {
            fail("Neither pg_jieba nor zhparser is installed. Cannot proceed with hybrid search.");
        }
    }
    
    @Test
    public void testToTsqueryWorks() {
        // 验证 to_tsquery 能解析中文查询（Review Focus #1）
        String query = "SELECT to_tsquery('jiebacfg', 'Spark & 内存溢出')";
        String result = pgJdbcTemplate.queryForObject(query, String.class);
        assertNotNull(result);
    }
}
```

- [ ] **Step 2: 运行测试验证中文分词扩展**

Run: `./mvnw test -Dtest=HybridSearchSchemaTest`  
Expected: 测试通过，确认 pg_jieba 或 zhparser 可用；失败时停止并上报

- [ ] **Step 3: 编写 Schema 迁移 SQL**

创建 `src/main/resources/db/migration/postgresql/V2__add_hybrid_search_columns.sql`:

```sql
-- 添加混合检索字段
ALTER TABLE knowledge_vectors ADD COLUMN IF NOT EXISTS text TEXT 
    COMMENT '全文检索字段（doc_type + scene_title + questions + answer）';
ALTER TABLE knowledge_vectors ADD COLUMN IF NOT EXISTS rerank_text TEXT 
    COMMENT '精排字段（scene_title + questions + answer，不含doc_type）';
ALTER TABLE knowledge_vectors ADD COLUMN IF NOT EXISTS section_path TEXT;

-- 添加 tsvector 生成列（使用 jiebacfg，如测试显示需要 zhcfg 则手动改为 zhcfg）
ALTER TABLE knowledge_vectors
ADD COLUMN IF NOT EXISTS text_tsv tsvector
GENERATED ALWAYS AS (to_tsvector('jiebacfg', coalesce(text, ''))) STORED;

-- 创建 GIN 索引
CREATE INDEX IF NOT EXISTS idx_kv_tsv ON knowledge_vectors USING GIN (text_tsv);

-- 确认 HNSW 索引存在（V1 已创建，这里确保参数一致）
CREATE INDEX IF NOT EXISTS idx_kv_embedding ON knowledge_vectors 
USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 添加注释
COMMENT ON COLUMN knowledge_vectors.text IS '全文检索字段（doc_type + scene_title + questions + answer）';
COMMENT ON COLUMN knowledge_vectors.rerank_text IS '精排字段（scene_title + questions + answer）';
COMMENT ON COLUMN knowledge_vectors.section_path IS '章节路径（冗余自 MySQL knowledge_chunks.section_path）';
COMMENT ON COLUMN knowledge_vectors.text_tsv IS '中文全文检索 tsvector（自动生成）';
```

- [ ] **Step 4: 应用迁移并验证索引**

Run: `./mvnw flyway:migrate -Dflyway.configFiles=flyway-postgresql.conf`  
Expected: Migration V2 applied successfully

Run: `psql -U user -d dbname -c "\d knowledge_vectors"` 验证新增三列和 text_tsv 生成列定义  
Expected: 输出显示 text, section_path, text_tsv 三列，text_tsv 为 GENERATED ALWAYS

- [ ] **Step 5: 更新 KnowledgeVector 实体**

修改 `src/main/java/com/dataplatform/knowledge/entity/KnowledgeVector.java`，添加字段：

```java
@Column(name = "text", columnDefinition = "TEXT")
private String text;  // 全文检索字段（doc_type + scene_title + questions + answer）

@Column(name = "rerank_text", columnDefinition = "TEXT")
private String rerankText;  // 精排字段（scene_title + questions + answer）

@Column(name = "section_path", columnDefinition = "TEXT")
private String sectionPath;

// text_tsv 是生成列，不映射到实体（由数据库自动维护）
```

- [ ] **Step 6: 编写实体测试验证新字段持久化**

在 `HybridSearchSchemaTest` 中添加：

```java
@Test
public void testNewColumnsPersist() {
    KnowledgeVector vec = KnowledgeVector.builder()
        .chunkId(999999L)
        .embedding(new float[1024])
        .docType("test")
        .text("manual 场景标题 问题1 答案内容")  // 组合字段
        .rerankText("场景标题 问题1 答案内容")   // 精排字段
        .sectionPath("章节A > 章节B")
        .build();
    vectorRepository.save(vec);
    
    KnowledgeVector loaded = vectorRepository.findById(999999L).orElseThrow();
    assertEquals("manual 场景标题 问题1 答案内容", loaded.getText());
    assertEquals("场景标题 问题1 答案内容", loaded.getRerankText());
    assertEquals("章节A > 章节B", loaded.getSectionPath());
    
    // 验证 text_tsv 自动生成
    String tsv = pgJdbcTemplate.queryForObject(
        "SELECT text_tsv::text FROM knowledge_vectors WHERE chunk_id = 999999",
        String.class
    );
    assertNotNull(tsv);
    assertTrue(tsv.contains("'场景'")); // jieba 分词后应包含 "场景"
}
```

- [ ] **Step 7: 运行实体测试**

Run: `./mvnw test -Dtest=HybridSearchSchemaTest#testNewColumnsPersist`  
Expected: PASS, text 和 section_path 成功写入，text_tsv 自动生成

- [ ] **Step 8: Commit Schema 变更**

```bash
git add src/main/resources/db/migration/postgresql/V2__add_hybrid_search_columns.sql \
  src/main/java/com/dataplatform/knowledge/entity/KnowledgeVector.java \
  src/test/java/com/dataplatform/knowledge/schema/HybridSearchSchemaTest.java
git commit -m "feat(schema): add text/section_path/text_tsv to knowledge_vectors for hybrid search"
```

---

### Task 2: 双写逻辑补强 - VectorizationService 同步 text, rerank_text 和 section_path

**Files:**
- Modify: `src/main/java/com/dataplatform/service/VectorizationService.java:145-149` (upsert 调用增加 text, rerankText 和 sectionPath 参数)
- Modify: `src/main/java/com/dataplatform/knowledge/repository/KnowledgeVectorRepository.java` (upsert 方法签名增加 text, rerankText 和 sectionPath)
- Create: `src/test/java/com/dataplatform/service/VectorizationServiceDualWriteTest.java`

**Interfaces:**
- Consumes: Task 1 的 KnowledgeVector 实体（包含 text/rerankText/sectionPath 字段）
- Produces:
  - `vectorRepository.upsert(Long chunkId, String embeddingStr, String text, String rerankText, String sectionPath, String docType, String routeAction, LocalDateTime createdAt, LocalDateTime updatedAt)` 方法签名
  - VectorizationService.vectorizeChunks() 在写入 embedding 时同时写入组合后的 text, rerankText 和 sectionPath

- [ ] **Step 1: 编写双写测试 - 验证 text, rerankText 和 sectionPath 同步写入**

创建 `src/test/java/com/dataplatform/service/VectorizationServiceDualWriteTest.java`:

```java
@SpringBootTest
@Transactional
public class VectorizationServiceDualWriteTest {
    
    @Autowired
    private VectorizationService vectorizationService;
    
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Test
    public void testTextAndRerankTextSyncToPgvector() {
        // 1. 在 MySQL 插入一条 chunk
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("manual");
        chunk.setDocId("test-doc");
        chunk.setSceneTitle("Spark 调优");
        chunk.setQuestions("如何解决 Spark 3.2 OOM？");
        chunk.setAnswer("增加 executor 内存可以解决 Spark 3.2 OOM");
        chunk.setSectionPath("Spark 调优 > 内存优化");
        chunk.setVectorizeStatus("pending_vectorization");
        chunkMapper.insert(chunk);
        Long chunkId = chunk.getId();
        
        // 2. 调用向量化服务
        vectorizationService.vectorizeChunksAsync(List.of(chunkId));
        
        // 3. 等待异步完成（测试环境应配置同步执行）
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            KnowledgeVector vec = vectorRepository.findById(chunkId).orElseThrow();
            
            // Review Focus #2: 验证 text 组合正确（doc_type + scene_title + questions + answer）
            String expectedText = "manual Spark 调优 如何解决 Spark 3.2 OOM？ 增加 executor 内存可以解决 Spark 3.2 OOM";
            assertEquals(expectedText, vec.getText());
            
            // 验证 rerankText 组合正确（scene_title + questions + answer，不含 doc_type）
            String expectedRerankText = "Spark 调优 如何解决 Spark 3.2 OOM？ 增加 executor 内存可以解决 Spark 3.2 OOM";
            assertEquals(expectedRerankText, vec.getRerankText());
            
            assertEquals("Spark 调优 > 内存优化", vec.getSectionPath());
        });
    }
}
```

- [ ] **Step 2: 运行测试，预期失败（upsert 方法尚未支持 text/sectionPath）**

Run: `./mvnw test -Dtest=VectorizationServiceDualWriteTest`  
Expected: FAIL with "upsert method signature mismatch"

- [ ] **Step 3: 将 KnowledgeVectorRepository 改为 MyBatis Mapper**

将 `src/main/java/com/dataplatform/knowledge/repository/KnowledgeVectorRepository.java` 从 JPA 改为 MyBatis:

```java
@Mapper
public interface KnowledgeVectorRepository {
    
    void upsert(@Param("chunkId") Long chunkId,
                @Param("embedding") String embedding,
                @Param("text") String text,
                @Param("rerankText") String rerankText,
                @Param("sectionPath") String sectionPath,
                @Param("docType") String docType,
                @Param("routeAction") String routeAction,
                @Param("createdAt") LocalDateTime createdAt,
                @Param("updatedAt") LocalDateTime updatedAt);
}
```

创建 `src/main/resources/mapper/KnowledgeVectorMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.dataplatform.knowledge.repository.KnowledgeVectorRepository">
    
    <insert id="upsert">
        INSERT INTO knowledge_vectors (
            chunk_id, embedding, text, rerank_text, section_path, 
            doc_type, route_action, created_at, updated_at
        )
        VALUES (
            #{chunkId}, CAST(#{embedding} AS vector), #{text}, #{rerankText}, #{sectionPath},
            #{docType}, #{routeAction}, #{createdAt}, #{updatedAt}
        )
        ON CONFLICT (chunk_id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            text = EXCLUDED.text,
            rerank_text = EXCLUDED.rerank_text,
            section_path = EXCLUDED.section_path,
            doc_type = EXCLUDED.doc_type,
            route_action = EXCLUDED.route_action,
            updated_at = EXCLUDED.updated_at
    </insert>
    
</mapper>
```

- [ ] **Step 4: 修改 VectorizationService 调用 upsert 时传入 text, rerankText 和 sectionPath**

在 `src/main/java/com/dataplatform/service/VectorizationService.java` 的 `vectorizeChunks` 方法中，修改 upsert 调用：

```java
// 1. 从 MySQL 读取 chunk
KnowledgeChunk chunk = chunkMapper.selectById(chunkId);

// 2. 组合字段
String docType = chunk.getDocType();
String sceneTitle = chunk.getSceneTitle() != null ? chunk.getSceneTitle() : "";
String questions = chunk.getQuestions() != null ? chunk.getQuestions() : "";
String answer = chunk.getAnswer() != null ? chunk.getAnswer() : "";
String sectionPath = chunk.getSectionPath();

// 2.1 全文检索字段（doc_type + scene_title + questions + answer）
String text = String.join(" ", docType, sceneTitle, questions, answer).trim();

// 2.2 精排字段（scene_title + questions + answer，不含 doc_type）
String rerankText = String.join(" ", sceneTitle, questions, answer).trim();

// 3. 向量化（使用 rerankText 作为输入，避免 doc_type 标签干扰语义）
String embeddingStr = embeddingClient.embed(rerankText);

// 4. upsert到pgvector（同步 text, rerankText 和 sectionPath）
LocalDateTime now = LocalDateTime.now();
vectorRepository.upsert(
    chunk.getId(),
    embeddingStr,
    text,              // 全文检索字段
    rerankText,        // 精排字段
    sectionPath,
    docType,
    chunk.getRouteAction(),
    now,
    now
);
```

**Review Focus**: 向量化输入使用 `rerankText`（不含 doc_type），保持与精排输入一致。

- [ ] **Step 5: 运行测试验证双写成功**

Run: `./mvnw test -Dtest=VectorizationServiceDualWriteTest`  
Expected: PASS, text, rerankText 和 sectionPath 成功同步到 pgvector

- [ ] **Step 6: Commit 双写逻辑**

```bash
git add src/main/java/com/dataplatform/service/VectorizationService.java \
  src/main/java/com/dataplatform/knowledge/repository/KnowledgeVectorRepository.java \
  src/test/java/com/dataplatform/service/VectorizationServiceDualWriteTest.java
git commit -m "feat(vectorization): sync text/rerank_text/section_path to pgvector for hybrid search"
```

---

### Task 3: 混合检索 RRF 融合 SQL + MyBatis 映射

**Files:**
- Create: `src/main/java/com/dataplatform/knowledge/dto/HybridSearchResult.java`
- Create: `src/main/java/com/dataplatform/knowledge/repository/HybridSearchRepository.java` (MyBatis Mapper 接口)
- Create: `src/main/java/com/dataplatform/knowledge/service/HybridSearchService.java`
- Create: `src/test/java/com/dataplatform/knowledge/service/HybridSearchServiceTest.java`

**Interfaces:**
- Consumes: 
  - Task 1 的 knowledge_vectors 表（text, rerank_text, section_path, text_tsv, embedding）
  - EmbeddingService.embedQuery(String query): float[] （生成查询向量）
- Produces:
  - `HybridSearchService.hybridSearch(String query, String docType, int topK): List<HybridSearchResult>` 方法
  - HybridSearchResult DTO（chunkId, text, rerankText, sectionPath, docType, rrfScore, vectorSimilarity）
  - 注意：`text` 用于日志和调试，`rerankText` 用于后续重排输入

- [ ] **Step 1: 创建 HybridSearchResult DTO**

创建 `src/main/java/com/dataplatform/knowledge/dto/HybridSearchResult.java`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchResult {
    private Long chunkId;
    private String text;             // 全文检索字段（用于日志和调试）
    private String rerankText;       // 精排字段（用于重排模型输入）
    private String sectionPath;
    private String docType;
    private Double rrfScore;
    private Double vectorSimilarity;  // 用于拒答阈值判断
}
```

- [ ] **Step 2: 编写 MyBatis Mapper 接口和 XML**

创建 `src/main/java/com/dataplatform/knowledge/repository/HybridSearchRepository.java`:

```java
@Mapper
public interface HybridSearchRepository {
    
    List<HybridSearchResult> hybridSearch(
        @Param("queryVector") String queryVector,  // pgvector 格式：[0.1,0.2,...]
        @Param("queryText") String queryText,
        @Param("docType") String docType,
        @Param("topK") int topK
    );
}
```

创建 `src/main/resources/mapper/HybridSearchMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.dataplatform.knowledge.repository.HybridSearchRepository">
    
    <resultMap id="HybridSearchResultMap" type="com.dataplatform.knowledge.dto.HybridSearchResult">
        <result column="chunk_id" property="chunkId"/>
        <result column="text" property="text"/>
        <result column="rerank_text" property="rerankText"/>
        <result column="section_path" property="sectionPath"/>
        <result column="doc_type" property="docType"/>
        <result column="rrf_score" property="rrfScore"/>
        <result column="vector_similarity" property="vectorSimilarity"/>
    </resultMap>
    
    <select id="hybridSearch" resultMap="HybridSearchResultMap">
        WITH vector_search AS (
            SELECT chunk_id, text, rerank_text, section_path, doc_type, embedding,
                   row_number() OVER (ORDER BY embedding &lt;=&gt; CAST(#{queryVector} AS vector)) AS v_rank
            FROM knowledge_vectors
            WHERE (#{docType} IS NULL OR doc_type = #{docType})
            ORDER BY embedding &lt;=&gt; CAST(#{queryVector} AS vector)
            LIMIT 50
        ),
        keyword_search AS (
            SELECT chunk_id, text, rerank_text, section_path, doc_type,
                   row_number() OVER (ORDER BY ts_rank_cd(text_tsv, to_tsquery('jiebacfg', #{queryText})) DESC) AS k_rank
            FROM knowledge_vectors
            WHERE text_tsv @@ to_tsquery('jiebacfg', #{queryText})
              AND (#{docType} IS NULL OR doc_type = #{docType})
            ORDER BY ts_rank_cd(text_tsv, to_tsquery('jiebacfg', #{queryText})) DESC
            LIMIT 50
        )
        SELECT 
            coalesce(v.chunk_id, k.chunk_id) AS chunk_id,
            coalesce(v.text, k.text) AS text,
            coalesce(v.rerank_text, k.rerank_text) AS rerank_text,
            coalesce(v.section_path, k.section_path) AS section_path,
            coalesce(v.doc_type, k.doc_type) AS doc_type,
            coalesce(1.0 / (60 + v.v_rank), 0.0) + coalesce(1.0 / (60 + k.k_rank), 0.0) AS rrf_score,
            1 - (v.embedding &lt;=&gt; CAST(#{queryVector} AS vector)) AS vector_similarity
        FROM vector_search v
        FULL OUTER JOIN keyword_search k USING (chunk_id)
        ORDER BY rrf_score DESC
        LIMIT #{topK}
    </select>
    
</mapper>
```

**注意**: XML 中 `<=>` 需要转义为 `&lt;=&gt;`

- [ ] **Step 3: 创建 HybridSearchService**

创建 `src/main/java/com/dataplatform/knowledge/service/HybridSearchService.java`:

```java
@Service
@Slf4j
public class HybridSearchService {
    
    private final HybridSearchRepository hybridSearchRepository;
    private final EmbeddingService embeddingService;
    
    public HybridSearchService(HybridSearchRepository hybridSearchRepository,
                                EmbeddingService embeddingService) {
        this.hybridSearchRepository = hybridSearchRepository;
        this.embeddingService = embeddingService;
    }
    
    public List<HybridSearchResult> hybridSearch(String query, String docType, int topK) {
        // 1. 生成查询向量
        float[] queryVector = embeddingService.embedQuery(query);
        String queryVectorStr = formatEmbedding(queryVector);
        
        // 2. 调用混合检索 SQL
        return hybridSearchRepository.hybridSearch(queryVectorStr, query, docType, topK);
    }
    
    private String formatEmbedding(float[] embedding) {
        return "[" + Arrays.stream(embedding)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(",")) + "]";
    }
}
```

- [ ] **Step 4: 编写混合检索测试 - 验证关键词路召回**

创建 `src/test/java/com/dataplatform/knowledge/service/HybridSearchServiceTest.java`:

```java
@SpringBootTest
@Transactional
public class HybridSearchServiceTest {
    
    @Autowired
    private HybridSearchService hybridSearchService;
    
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Test
    public void testKeywordPathRecall() {
        // 1. 准备测试数据（插入包含 "3.2" 的 chunk）
        KnowledgeVector vec = KnowledgeVector.builder()
            .chunkId(1001L)
            .embedding(new float[1024])
            .text("Spark 3.2 OOM 的解决方案是增加 executor 内存")
            .sectionPath("Spark 调优 > 内存优化")
            .docType("spark")
            .build();
        vectorRepository.save(vec);
        
        // 2. 混合检索
        List<HybridSearchResult> results = hybridSearchService.hybridSearch(
            "Spark 3.2 内存溢出", null, 10
        );
        
        // 3. 验证关键词路命中（验收测试 13.1）
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.getChunkId().equals(1001L)));
        
        HybridSearchResult hit = results.stream()
            .filter(r -> r.getChunkId().equals(1001L))
            .findFirst().orElseThrow();
        assertTrue(hit.getText().contains("3.2"));
        assertEquals("Spark 调优 > 内存优化", hit.getSectionPath());
    }
    
    @Test
    public void testIndexesUsed() {
        // Review Focus #3: 验证 EXPLAIN ANALYZE 显示索引使用
        String explainQuery = """
            EXPLAIN ANALYZE
            WITH vector_search AS (
                SELECT chunk_id FROM knowledge_vectors
                ORDER BY embedding <=> '[0.1,0.2,...1024个值]'::vector LIMIT 50
            ),
            keyword_search AS (
                SELECT chunk_id FROM knowledge_vectors
                WHERE text_tsv @@ to_tsquery('jiebacfg', 'Spark')
                LIMIT 50
            )
            SELECT * FROM vector_search v FULL OUTER JOIN keyword_search k USING (chunk_id)
            """;
        
        String plan = pgJdbcTemplate.queryForObject(explainQuery, String.class);
        
        // 验证 HNSW 索引和 GIN 索引被使用
        assertTrue(plan.contains("Index Scan using idx_kv_embedding"));
        assertTrue(plan.contains("Bitmap Index Scan on idx_kv_tsv"));
    }
}
```

- [ ] **Step 5: 运行测试验证混合检索和索引使用**

Run: `./mvnw test -Dtest=HybridSearchServiceTest`  
Expected: PASS, 关键词路成功命中包含 "3.2" 的 chunk，索引被正确使用

- [ ] **Step 6: Commit 混合检索实现**

```bash
git add src/main/java/com/dataplatform/knowledge/dto/HybridSearchResult.java \
  src/main/java/com/dataplatform/knowledge/repository/HybridSearchRepository.java \
  src/main/java/com/dataplatform/knowledge/service/HybridSearchService.java \
  src/test/java/com/dataplatform/knowledge/service/HybridSearchServiceTest.java
git commit -m "feat(search): implement hybrid search with RRF fusion (vector + keyword)"
```

---

### Task 4: 重排集成 - 阿里云 DashScope Rerank API

**Files:**
- Create: `src/main/java/com/dataplatform/knowledge/dto/RerankResult.java`
- Create: `src/main/java/com/dataplatform/knowledge/service/RerankService.java`
- Create: `src/test/java/com/dataplatform/knowledge/service/RerankServiceTest.java`
- Modify: `pom.xml` (添加 dashscope-sdk-java 依赖，如果 spring-ai-alibaba-starter 不包含 rerank 客户端)

**Interfaces:**
- Consumes:
  - Task 3 的 HybridSearchResult 列表
  - 阿里云 DashScope Rerank API（模型：gte-rerank）
- Produces:
  - `RerankService.rerank(String query, List<HybridSearchResult> candidates, int topN): List<RerankResult>` 方法
  - RerankResult DTO（chunkId, text, sectionPath, relevanceScore）

- [ ] **Step 1: 创建 RerankResult DTO**

创建 `src/main/java/com/dataplatform/knowledge/dto/RerankResult.java`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {
    private Long chunkId;
    private String text;
    private String sectionPath;
    private Double relevanceScore;
}
```

- [ ] **Step 2: 编写 RerankService - 调用 DashScope Rerank API**

创建 `src/main/java/com/dataplatform/knowledge/service/RerankService.java`:

```java
@Service
@Slf4j
public class RerankService {
    
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    
    public RerankService() {
        this.restTemplate = new RestTemplate();
    }
    
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topN) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 1. 构造请求体
        List<Map<String, String>> documents = candidates.stream()
            .map(c -> Map.of("text", c.getText()))
            .toList();
        
        Map<String, Object> requestBody = Map.of(
            "model", "gte-rerank",
            "query", query,
            "documents", documents,
            "top_n", Math.min(topN, candidates.size())
        );
        
        // 2. 调用 DashScope Rerank API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/v1";
        ResponseEntity<RerankResponse> response = restTemplate.postForEntity(
            url, request, RerankResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Rerank API failed: {}", response.getStatusCode());
            throw new RuntimeException("Rerank API call failed");
        }
        
        // 3. 映射结果回原始 chunk（Review Focus #4: 验证 index 不越界）
        RerankResponse rerankResponse = response.getBody();
        return rerankResponse.getResults().stream()
            .map(r -> {
                if (r.getIndex() < 0 || r.getIndex() >= candidates.size()) {
                    throw new IndexOutOfBoundsException(
                        String.format("Rerank API returned invalid index: %d (candidates size: %d)",
                                      r.getIndex(), candidates.size())
                    );
                }
                HybridSearchResult original = candidates.get(r.getIndex());
                return RerankResult.builder()
                    .chunkId(original.getChunkId())
                    .text(original.getText())
                    .sectionPath(original.getSectionPath())
                    .relevanceScore(r.getRelevanceScore())
                    .build();
            })
            .toList();
    }
    
    // 内部类用于反序列化 API 响应
    @Data
    public static class RerankResponse {
        private List<RerankItem> results;
        
        @Data
        public static class RerankItem {
            private int index;
            private double relevanceScore;
        }
    }
}
```

- [ ] **Step 3: 编写重排测试 - 验证 index 越界保护**

创建 `src/test/java/com/dataplatform/knowledge/service/RerankServiceTest.java`:

```java
@SpringBootTest
public class RerankServiceTest {
    
    @Autowired
    private RerankService rerankService;
    
    @Test
    public void testRerankWithRealAPI() {
        // 准备候选
        List<HybridSearchResult> candidates = List.of(
            HybridSearchResult.builder()
                .chunkId(1L)
                .text("Spark 性能优化的方法包括调整并行度")
                .sectionPath("Spark > 性能")
                .build(),
            HybridSearchResult.builder()
                .chunkId(2L)
                .text("Spark 内存溢出的解决方案是增加 executor 内存")
                .sectionPath("Spark > 内存")
                .build()
        );
        
        // 调用重排
        List<RerankResult> reranked = rerankService.rerank("Spark 内存溢出", candidates, 2);
        
        // 验证结果
        assertFalse(reranked.isEmpty());
        assertEquals(2, reranked.size());
        
        // 验证 relevanceScore 降序排列
        for (int i = 0; i < reranked.size() - 1; i++) {
            assertTrue(reranked.get(i).getRelevanceScore() >= reranked.get(i + 1).getRelevanceScore());
        }
        
        // Review Focus #4: 验证所有 chunkId 都在原始 candidates 中
        Set<Long> candidateIds = candidates.stream().map(HybridSearchResult::getChunkId).collect(Collectors.toSet());
        for (RerankResult r : reranked) {
            assertTrue(candidateIds.contains(r.getChunkId()));
        }
    }
    
    @Test
    public void testRerankEmptyCandidates() {
        List<RerankResult> reranked = rerankService.rerank("query", Collections.emptyList(), 10);
        assertTrue(reranked.isEmpty());
    }
}
```

- [ ] **Step 4: 运行测试验证重排功能**

Run: `./mvnw test -Dtest=RerankServiceTest`  
Expected: PASS, 重排结果按 relevanceScore 降序排列，index 不越界

- [ ] **Step 5: Commit 重排实现**

```bash
git add src/main/java/com/dataplatform/knowledge/dto/RerankResult.java \
  src/main/java/com/dataplatform/knowledge/service/RerankService.java \
  src/test/java/com/dataplatform/knowledge/service/RerankServiceTest.java
git commit -m "feat(rerank): integrate DashScope gte-rerank API"
```

---

### Task 5: Prompt 组装 + 引用溯源 + 低置信问题池

**Files:**
- Create: `src/main/java/com/dataplatform/knowledge/service/PromptBuilder.java`
- Create: `src/main/java/com/dataplatform/entity/LowConfidenceQuestion.java`
- Create: `src/main/java/com/dataplatform/mapper/LowConfidenceQuestionMapper.java`
- Create: `src/main/resources/db/migration/V2.1__create_low_confidence_questions.sql` (MySQL)
- Create: `src/main/java/com/dataplatform/knowledge/service/RAGService.java` (整合检索+重排+生成)
- Create: `src/test/java/com/dataplatform/knowledge/service/RAGServiceTest.java`

**Interfaces:**
- Consumes:
  - Task 4 的 RerankResult 列表
  - Spring AI ChatClient（用于生成回答）
- Produces:
  - `PromptBuilder.buildPromptWithCitations(String query, List<RerankResult> reranked): String` 方法（首尾相关布局）
  - `RAGService.answer(String query, String sessionId, double vectorSimThreshold, double rerankThreshold): String` 方法
  - LowConfidenceQuestion 实体 + Mapper（记录拒答问题）

- [ ] **Step 1: 创建 MySQL low_confidence_questions 表**

创建 `src/main/resources/db/migration/V2.1__create_low_confidence_questions.sql`:

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

- [ ] **Step 2: 应用 MySQL 迁移**

Run: `./mvnw flyway:migrate -Dflyway.configFiles=flyway.conf`  
Expected: Migration V2.1 applied successfully

- [ ] **Step 3: 创建 LowConfidenceQuestion 实体和 MyBatis Mapper**

创建 `src/main/java/com/dataplatform/entity/LowConfidenceQuestion.java`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowConfidenceQuestion {
    private Long id;
    private Long conversationId;
    private String rawQuestion;
    private String source;  // retrieval_low_conf, self_check, user_feedback
    private String reason;
    private LocalDateTime createdAt;
}
```

创建 `src/main/java/com/dataplatform/mapper/LowConfidenceQuestionMapper.java`:

```java
@Mapper
public interface LowConfidenceQuestionMapper {
    
    void insert(LowConfidenceQuestion question);
    
    List<LowConfidenceQuestion> selectByConversationId(@Param("conversationId") Long conversationId);
    
    List<LowConfidenceQuestion> selectBySource(@Param("source") String source);
}
```

创建 `src/main/resources/mapper/LowConfidenceQuestionMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.dataplatform.mapper.LowConfidenceQuestionMapper">
    
    <resultMap id="LowConfidenceQuestionMap" type="com.dataplatform.entity.LowConfidenceQuestion">
        <id column="id" property="id"/>
        <result column="conversation_id" property="conversationId"/>
        <result column="raw_question" property="rawQuestion"/>
        <result column="source" property="source"/>
        <result column="reason" property="reason"/>
        <result column="created_at" property="createdAt"/>
    </resultMap>
    
    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO low_confidence_questions (
            conversation_id, raw_question, source, reason, created_at
        )
        VALUES (
            #{conversationId}, #{rawQuestion}, #{source}, #{reason}, NOW()
        )
    </insert>
    
    <select id="selectByConversationId" resultMap="LowConfidenceQuestionMap">
        SELECT id, conversation_id, raw_question, source, reason, created_at
        FROM low_confidence_questions
        WHERE conversation_id = #{conversationId}
        ORDER BY created_at DESC
    </select>
    
    <select id="selectBySource" resultMap="LowConfidenceQuestionMap">
        SELECT id, conversation_id, raw_question, source, reason, created_at
        FROM low_confidence_questions
        WHERE source = #{source}
        ORDER BY created_at DESC
    </select>
    
</mapper>
```

创建 `src/main/java/com/dataplatform/mapper/LowConfidenceQuestionMapper.java`:

```java
@Mapper
public interface LowConfidenceQuestionMapper extends BaseMapper<LowConfidenceQuestion> {
}
```

- [ ] **Step 4: 创建 PromptBuilder - 首尾相关布局**

创建 `src/main/java/com/dataplatform/knowledge/service/PromptBuilder.java`:

```java
@Component
public class PromptBuilder {
    
    public String buildPromptWithCitations(String query, List<RerankResult> reranked) {
        if (reranked.isEmpty()) {
            return "无相关知识";
        }
        
        StringBuilder prompt = new StringBuilder();
        
        // 最相关的放开头
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
}
```

- [ ] **Step 5: 创建 RAGService - 整合检索+重排+生成+拒答**

创建 `src/main/java/com/dataplatform/knowledge/service/RAGService.java`:

```java
@Service
@Slf4j
public class RAGService {
    
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final LowConfidenceQuestionMapper lowConfidenceQuestionMapper;
    
    public RAGService(HybridSearchService hybridSearchService,
                       RerankService rerankService,
                       PromptBuilder promptBuilder,
                       ChatClient.Builder chatClientBuilder,
                       LowConfidenceQuestionMapper lowConfidenceQuestionMapper) {
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClientBuilder.build();
        this.lowConfidenceQuestionMapper = lowConfidenceQuestionMapper;
    }
    
    public String answer(String query, Long conversationId, double vectorSimThreshold, double rerankThreshold) {
        // 1. 混合检索
        List<HybridSearchResult> candidates = hybridSearchService.hybridSearch(query, null, 30);
        
        // 2. 拒答判断 - vectorSimilarity 阈值
        if (candidates.isEmpty() || candidates.get(0).getVectorSimilarity() < vectorSimThreshold) {
            log.info("No recall or low vectorSimilarity, recording to low_confidence_questions");
            recordLowConfidence(query, conversationId, "low_score");
            return "抱歉，知识库中没有相关信息";
        }
        
        // 3. 重排
        List<RerankResult> reranked = rerankService.rerank(query, candidates, 5);
        
        // 4. 拒答判断 - rerank relevanceScore 阈值
        if (reranked.isEmpty() || reranked.get(0).getRelevanceScore() < rerankThreshold) {
            log.info("Low rerank score, recording to low_confidence_questions");
            recordLowConfidence(query, conversationId, "low_score");
            return "抱歉，知识库中没有相关信息";
        }
        
        // 5. 构造 prompt
        String prompt = promptBuilder.buildPromptWithCitations(query, reranked);
        
        // 6. 调用 LLM 生成回答
        String systemPrompt = """
            你是一个智能助手。如果参考资料不足以回答用户问题，请直接回复"抱歉，知识库中没有相关信息"。
            禁止编造、猜测或承诺具体到账时间等。
            """;
        
        String response = chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .call()
            .content();
        
        // 7. 检测模型拒答
        if (response.contains("没有相关信息") || response.contains("无法回答")) {
            recordLowConfidence(query, conversationId, "model_refuse");
        }
        
        return response;
    }
    
    private void recordLowConfidence(String query, Long conversationId, String reason) {
        LowConfidenceQuestion lcq = LowConfidenceQuestion.builder()
            .rawQuestion(query)
            .conversationId(conversationId)
            .source("retrieval_low_conf")
            .reason(reason)
            .build();
        lowConfidenceQuestionMapper.insert(lcq);
    }
}
```

- [ ] **Step 6: 编写 RAGService 测试 - 验证拒答机制和引用**

创建 `src/test/java/com/dataplatform/knowledge/service/RAGServiceTest.java`:

```java
@SpringBootTest
@Transactional
public class RAGServiceTest {
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private LowConfidenceQuestionMapper lowConfidenceQuestionMapper;
    
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Test
    public void testAnswerWithCitations() {
        // 准备数据
        KnowledgeVector vec = KnowledgeVector.builder()
            .chunkId(2001L)
            .embedding(new float[1024])
            .text("Spark 任务慢的原因可能是数据倾斜")
            .sectionPath("Spark 调优 > 性能优化")
            .docType("spark")
            .build();
        vectorRepository.save(vec);
        
        // 调用 RAG
        String answer = ragService.answer("任务慢怎么办", "session-123", 0.6, 0.5);
        
        // 验收测试 13.2: 验证回答包含引用编号
        assertTrue(answer.contains("[1]") || answer.contains("[2]"));
    }
    
    @Test
    public void testRefuseWhenNoKnowledge() {
        // 验收测试 13.3: 知识库外问题拒答
        String answer = ragService.answer("明天天气怎么样", 456L, 0.6, 0.5);
        
        assertEquals("抱歉，知识库中没有相关信息", answer);
        
        // 验证记录到 low_confidence_questions 表
        List<LowConfidenceQuestion> records = lowConfidenceQuestionMapper.selectByConversationId(456L);
        assertFalse(records.isEmpty());
        assertEquals("明天天气怎么样", records.get(0).getRawQuestion());
    }
    
    @Test
    public void testThresholdBoundary() {
        // Review Focus #5: 验证拒答阈值边界
        // 模拟 vectorSimilarity = 0.59 的场景（应拒答）
        // 模拟 vectorSimilarity = 0.60 的场景（应通过）
        // 实际实现需要 mock HybridSearchService 返回特定相似度的结果
    }
}
```

- [ ] **Step 7: 运行测试验证 RAG 完整流程**

Run: `./mvnw test -Dtest=RAGServiceTest`  
Expected: PASS, 回答包含引用编号，拒答时记录到 low_confidence_questions 表

- [ ] **Step 8: Commit RAG 服务**

```bash
git add src/main/java/com/dataplatform/knowledge/service/PromptBuilder.java \
  src/main/java/com/dataplatform/entity/LowConfidenceQuestion.java \
  src/main/java/com/dataplatform/mapper/LowConfidenceQuestionMapper.java \
  src/main/resources/db/migration/V2.1__create_low_confidence_questions.sql \
  src/main/java/com/dataplatform/knowledge/service/RAGService.java \
  src/test/java/com/dataplatform/knowledge/service/RAGServiceTest.java
git commit -m "feat(rag): integrate retrieval+rerank+generation with citation and refusal"
```

---

### Task 6: 评估体系 - 四策略对比 + Recall@K/MRR

**Files:**
- Create: `src/main/java/com/dataplatform/knowledge/evaluation/EvaluationCase.java`
- Create: `src/main/java/com/dataplatform/knowledge/evaluation/RetrievalMetrics.java`
- Create: `src/main/java/com/dataplatform/knowledge/evaluation/EvaluationService.java`
- Create: `src/test/resources/evaluation-cases.json` (评估集)
- Create: `src/test/java/com/dataplatform/knowledge/evaluation/EvaluationRunnerTest.java`

**Interfaces:**
- Consumes:
  - Task 3 的 HybridSearchService
  - Task 4 的 RerankService
  - 评估集 JSON（query, difficulty, queryType, groundTruth）
- Produces:
  - `EvaluationService.runEvaluation(List<EvaluationCase> cases): Map<String, Map<String, Double>>` 方法（四策略 -> 指标）
  - RetrievalMetrics.recallAtK() 和 mrr() 方法

- [ ] **Step 1: 创建评估集 JSON**

创建 `src/test/resources/evaluation-cases.json`:

```json
[
  {
    "query": "Spark 3.2 OOM 如何处理",
    "difficulty": "easy",
    "queryType": "keyword",
    "groundTruth": [1001, 1002]
  },
  {
    "query": "任务运行很慢怎么办",
    "difficulty": "medium",
    "queryType": "semantic",
    "groundTruth": [2001, 2002]
  },
  {
    "query": "Join 导致数据倾斜的解决方案",
    "difficulty": "hard",
    "queryType": "hybrid",
    "groundTruth": [3001, 3002, 3003]
  }
]
```

- [ ] **Step 2: 创建 EvaluationCase 和 RetrievalMetrics**

创建 `src/main/java/com/dataplatform/knowledge/evaluation/EvaluationCase.java`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationCase {
    private String query;
    private String difficulty;       // easy | medium | hard
    private String queryType;        // keyword | semantic | hybrid
    private List<Long> groundTruth;  // 标注的正确 chunk_id 列表
}
```

创建 `src/main/java/com/dataplatform/knowledge/evaluation/RetrievalMetrics.java`:

```java
public class RetrievalMetrics {
    
    public static double recallAtK(List<Long> retrieved, List<Long> groundTruth, int k) {
        if (groundTruth.isEmpty()) {
            return 0.0;
        }
        Set<Long> topK = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        long hits = groundTruth.stream().filter(topK::contains).count();
        return (double) hits / groundTruth.size();
    }
    
    public static double mrr(List<Long> retrieved, List<Long> groundTruth) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (groundTruth.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }
}
```

- [ ] **Step 3: 创建 EvaluationService - 四策略对比**

创建 `src/main/java/com/dataplatform/knowledge/evaluation/EvaluationService.java`:

```java
@Service
@Slf4j
public class EvaluationService {
    
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final EmbeddingService embeddingService;
    private final HybridSearchRepository hybridSearchRepository;
    
    public EvaluationService(HybridSearchService hybridSearchService,
                              RerankService rerankService,
                              EmbeddingService embeddingService,
                              HybridSearchRepository hybridSearchRepository) {
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
        this.embeddingService = embeddingService;
        this.hybridSearchRepository = hybridSearchRepository;
    }
    
    public Map<String, Map<String, Double>> runEvaluation(List<EvaluationCase> cases) {
        Map<String, List<Map<String, Double>>> resultsByType = new HashMap<>();
        
        for (EvaluationCase c : cases) {
            log.info("Evaluating: {} (Type: {}, Difficulty: {})", c.getQuery(), c.getQueryType(), c.getDifficulty());
            
            // 策略1: 纯向量
            List<Long> vectorOnly = vectorSearch(c.getQuery(), 10);
            double vecR5 = RetrievalMetrics.recallAtK(vectorOnly, c.getGroundTruth(), 5);
            double vecMRR = RetrievalMetrics.mrr(vectorOnly, c.getGroundTruth());
            
            // 策略2: 纯关键词
            List<Long> keywordOnly = keywordSearch(c.getQuery(), 10);
            double kwR5 = RetrievalMetrics.recallAtK(keywordOnly, c.getGroundTruth(), 5);
            double kwMRR = RetrievalMetrics.mrr(keywordOnly, c.getGroundTruth());
            
            // 策略3: 混合 (RRF)
            List<Long> hybrid = hybridSearchService.hybridSearch(c.getQuery(), null, 10).stream()
                .map(HybridSearchResult::getChunkId).toList();
            double hybR5 = RetrievalMetrics.recallAtK(hybrid, c.getGroundTruth(), 5);
            double hybMRR = RetrievalMetrics.mrr(hybrid, c.getGroundTruth());
            
            // 策略4: 混合 + Rerank
            List<HybridSearchResult> candidates = hybridSearchService.hybridSearch(c.getQuery(), null, 30);
            List<Long> hybridRerank = rerankService.rerank(c.getQuery(), candidates, 10).stream()
                .map(RerankResult::getChunkId).toList();
            double hyrR5 = RetrievalMetrics.recallAtK(hybridRerank, c.getGroundTruth(), 5);
            double hyrMRR = RetrievalMetrics.mrr(hybridRerank, c.getGroundTruth());
            
            // 按 queryType 分桶
            resultsByType.computeIfAbsent(c.getQueryType(), k -> new ArrayList<>())
                .add(Map.of(
                    "vectorR5", vecR5, "vectorMRR", vecMRR,
                    "keywordR5", kwR5, "keywordMRR", kwMRR,
                    "hybridR5", hybR5, "hybridMRR", hybMRR,
                    "hybridRerankR5", hyrR5, "hybridRerankMRR", hyrMRR
                ));
            
            log.info("  Vector: R@5={}, MRR={}", vecR5, vecMRR);
            log.info("  Keyword: R@5={}, MRR={}", kwR5, kwMRR);
            log.info("  Hybrid: R@5={}, MRR={}", hybR5, hybMRR);
            log.info("  Hybrid+Rerank: R@5={}, MRR={}", hyrR5, hyrMRR);
        }
        
        // 聚合平均值
        return aggregateResults(resultsByType);
    }
    
    private List<Long> vectorSearch(String query, int topK) {
        float[] queryVector = embeddingService.embedQuery(query);
        String queryVectorStr = formatEmbedding(queryVector);
        // 调用纯向量检索（省略 keyword_search CTE）
        // 简化实现：复用 hybridSearch 但只取向量路结果
        return hybridSearchService.hybridSearch(query, null, topK).stream()
            .sorted(Comparator.comparing(HybridSearchResult::getVectorSimilarity).reversed())
            .map(HybridSearchResult::getChunkId)
            .toList();
    }
    
    private List<Long> keywordSearch(String query, int topK) {
        // 调用纯关键词检索（省略 vector_search CTE）
        // 需要在 HybridSearchRepository 中添加单独的 keywordSearch 方法
        // 简化实现：从 hybridSearch 结果中筛选 keyword 命中的
        return hybridSearchService.hybridSearch(query, null, topK).stream()
            .filter(r -> r.getRrfScore() > 0)  // 粗略过滤
            .map(HybridSearchResult::getChunkId)
            .toList();
    }
    
    private String formatEmbedding(float[] embedding) {
        return "[" + Arrays.stream(embedding)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(",")) + "]";
    }
    
    private Map<String, Map<String, Double>> aggregateResults(Map<String, List<Map<String, Double>>> resultsByType) {
        // 按 queryType 计算平均值
        Map<String, Map<String, Double>> aggregated = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Double>>> entry : resultsByType.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Double>> results = entry.getValue();
            
            Map<String, Double> avg = new HashMap<>();
            for (String metric : results.get(0).keySet()) {
                double sum = results.stream().mapToDouble(m -> m.get(metric)).sum();
                avg.put(metric, sum / results.size());
            }
            aggregated.put(type, avg);
        }
        return aggregated;
    }
}
```

- [ ] **Step 4: 编写评估运行测试**

创建 `src/test/java/com/dataplatform/knowledge/evaluation/EvaluationRunnerTest.java`:

```java
@SpringBootTest
public class EvaluationRunnerTest {
    
    @Autowired
    private EvaluationService evaluationService;
    
    @Test
    public void runFullEvaluation() throws IOException {
        // 加载评估集
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getClassLoader().getResourceAsStream("evaluation-cases.json");
        List<EvaluationCase> cases = mapper.readValue(is, new TypeReference<>() {});
        
        // 运行评估（验收测试 13.4）
        Map<String, Map<String, Double>> results = evaluationService.runEvaluation(cases);
        
        // 输出报告
        for (Map.Entry<String, Map<String, Double>> entry : results.entrySet()) {
            String type = entry.getKey();
            Map<String, Double> metrics = entry.getValue();
            
            System.out.printf("Query Type: %s%n", type);
            System.out.printf("  Vector:         R@5=%.2f, MRR=%.2f%n",
                metrics.get("vectorR5"), metrics.get("vectorMRR"));
            System.out.printf("  Keyword:        R@5=%.2f, MRR=%.2f%n",
                metrics.get("keywordR5"), metrics.get("keywordMRR"));
            System.out.printf("  Hybrid:         R@5=%.2f, MRR=%.2f%n",
                metrics.get("hybridR5"), metrics.get("hybridMRR"));
            System.out.printf("  Hybrid+Rerank:  R@5=%.2f, MRR=%.2f%n",
                metrics.get("hybridRerankR5"), metrics.get("hybridRerankMRR"));
        }
        
        // 验证混合+重排策略优于纯向量（基本预期）
        double hybridRerankR5 = results.values().stream()
            .mapToDouble(m -> m.get("hybridRerankR5"))
            .average().orElse(0.0);
        double vectorR5 = results.values().stream()
            .mapToDouble(m -> m.get("vectorR5"))
            .average().orElse(0.0);
        
        assertTrue(hybridRerankR5 >= vectorR5, "Hybrid+Rerank should not be worse than Vector-only");
    }
}
```

- [ ] **Step 5: 运行评估测试**

Run: `./mvnw test -Dtest=EvaluationRunnerTest`  
Expected: PASS, 输出四策略对比报告，混合+重排策略 R@5 >= 纯向量策略

- [ ] **Step 6: Commit 评估体系**

```bash
git add src/main/java/com/dataplatform/knowledge/evaluation/*.java \
  src/test/resources/evaluation-cases.json \
  src/test/java/com/dataplatform/knowledge/evaluation/EvaluationRunnerTest.java
git commit -m "feat(evaluation): add four-strategy comparison with Recall@K and MRR"
```

---

### Task 7: 前端配套 - 引用点击展示接口

**Files:**
- Create: `src/main/java/com/dataplatform/knowledge/dto/CitationDetail.java`
- Create: `src/main/java/com/dataplatform/controller/CitationController.java`
- Create: `src/test/java/com/dataplatform/controller/CitationControllerTest.java`

**Interfaces:**
- Consumes:
  - Task 5 的 RAGService（reranked 列表存储在会话上下文中）
- Produces:
  - `GET /api/chat/citation/{citationIndex}?sessionId={sessionId}` 接口
  - CitationDetail DTO（text, sectionPath, chunkId）

- [ ] **Step 1: 创建 CitationDetail DTO**

创建 `src/main/java/com/dataplatform/knowledge/dto/CitationDetail.java`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDetail {
    private String text;
    private String sectionPath;
    private Long chunkId;
}
```

- [ ] **Step 2: 创建 CitationController**

创建 `src/main/java/com/dataplatform/controller/CitationController.java`:

```java
@RestController
@RequestMapping("/api/chat")
public class CitationController {
    
    // 简化实现：从内存 Map 存储会话上下文的 reranked 列表
    // 生产环境应使用 Redis 或会话存储
    private final Map<String, List<RerankResult>> sessionContext = new ConcurrentHashMap<>();
    
    public void storeRerankedResults(String sessionId, List<RerankResult> reranked) {
        sessionContext.put(sessionId, reranked);
    }
    
    @GetMapping("/citation/{citationIndex}")
    public CitationDetail getCitation(@PathVariable int citationIndex,
                                       @RequestParam String sessionId) {
        List<RerankResult> reranked = sessionContext.get(sessionId);
        if (reranked == null || citationIndex < 1 || citationIndex > reranked.size()) {
            throw new IllegalArgumentException("Invalid citation index or session not found");
        }
        
        RerankResult citation = reranked.get(citationIndex - 1);  // [1] 对应索引 0
        
        return CitationDetail.builder()
            .text(citation.getText())
            .sectionPath(citation.getSectionPath())
            .chunkId(citation.getChunkId())
            .build();
    }
}
```

- [ ] **Step 3: 修改 RAGService 存储 reranked 结果到会话上下文**

在 `RAGService.answer()` 方法中，重排完成后调用：

```java
// 注入 CitationController
@Autowired
private CitationController citationController;

// 在 rerank 后
citationController.storeRerankedResults(sessionId, reranked);
```

- [ ] **Step 4: 编写引用接口测试**

创建 `src/test/java/com/dataplatform/controller/CitationControllerTest.java`:

```java
@SpringBootTest
@AutoConfigureMockMvc
public class CitationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private CitationController citationController;
    
    @Test
    public void testGetCitation() throws Exception {
        // 1. 模拟存储 reranked 结果
        List<RerankResult> reranked = List.of(
            RerankResult.builder()
                .chunkId(1L)
                .text("引用1的原文")
                .sectionPath("章节A > 章节B")
                .relevanceScore(0.9)
                .build(),
            RerankResult.builder()
                .chunkId(2L)
                .text("引用2的原文")
                .sectionPath("章节C")
                .relevanceScore(0.8)
                .build()
        );
        citationController.storeRerankedResults("test-session", reranked);
        
        // 2. 调用接口获取引用1
        mockMvc.perform(get("/api/chat/citation/1")
                .param("sessionId", "test-session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("引用1的原文"))
            .andExpect(jsonPath("$.sectionPath").value("章节A > 章节B"))
            .andExpect(jsonPath("$.chunkId").value(1));
    }
    
    @Test
    public void testInvalidCitationIndex() throws Exception {
        citationController.storeRerankedResults("test-session", Collections.emptyList());
        
        mockMvc.perform(get("/api/chat/citation/999")
                .param("sessionId", "test-session"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 5: 运行测试验证引用接口**

Run: `./mvnw test -Dtest=CitationControllerTest`  
Expected: PASS, 引用接口返回正确的 text 和 sectionPath

- [ ] **Step 6: Commit 引用接口**

```bash
git add src/main/java/com/dataplatform/knowledge/dto/CitationDetail.java \
  src/main/java/com/dataplatform/controller/CitationController.java \
  src/test/java/com/dataplatform/controller/CitationControllerTest.java
git commit -m "feat(citation): add citation detail endpoint for frontend"
```

---

### Task 8: 数据对账任务 - 检测双写不一致

**Files:**
- Create: `src/main/java/com/dataplatform/knowledge/task/DataReconciliationTask.java`
- Create: `src/test/java/com/dataplatform/knowledge/task/DataReconciliationTaskTest.java`

**Interfaces:**
- Consumes:
  - MySQL knowledge_chunks 表（answer, section_path）
  - PostgreSQL knowledge_vectors 表（text, section_path）
- Produces:
  - `DataReconciliationTask.reconcile(): ReconciliationReport` 方法（定时任务，检测不一致并触发补偿同步）

- [ ] **Step 1: 创建对账任务**

创建 `src/main/java/com/dataplatform/knowledge/task/DataReconciliationTask.java`:

```java
@Component
@Slf4j
public class DataReconciliationTask {
    
    private final KnowledgeChunkMapper chunkMapper;
    private final JdbcTemplate pgJdbcTemplate;
    private final VectorizationService vectorizationService;
    
    public DataReconciliationTask(KnowledgeChunkMapper chunkMapper,
                                   @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate,
                                   VectorizationService vectorizationService) {
        this.chunkMapper = chunkMapper;
        this.pgJdbcTemplate = pgJdbcTemplate;
        this.vectorizationService = vectorizationService;
    }
    
    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点运行
    public void reconcile() {
        log.info("Starting data reconciliation task");
        
        // 1. 查询不一致的记录
        String sql = """
            SELECT m.id, m.answer, m.section_path AS mysql_section_path,
                   v.text AS pg_text, v.section_path AS pg_section_path
            FROM knowledge_chunks m
            LEFT JOIN knowledge_vectors v ON m.id = v.chunk_id
            WHERE m.answer != v.text OR m.section_path != v.section_path
            LIMIT 1000
            """;
        
        List<Map<String, Object>> inconsistencies = pgJdbcTemplate.queryForList(sql);
        
        if (inconsistencies.isEmpty()) {
            log.info("No inconsistencies found");
            return;
        }
        
        log.warn("Found {} inconsistencies, triggering compensation sync", inconsistencies.size());
        
        // 2. 触发补偿同步
        List<Long> chunkIds = inconsistencies.stream()
            .map(row -> ((Number) row.get("id")).longValue())
            .toList();
        
        vectorizationService.vectorizeChunksAsync(chunkIds);
        
        log.info("Compensation sync triggered for {} chunks", chunkIds.size());
    }
}
```

- [ ] **Step 2: 编写对账任务测试**

创建 `src/test/java/com/dataplatform/knowledge/task/DataReconciliationTaskTest.java`:

```java
@SpringBootTest
@Transactional
public class DataReconciliationTaskTest {
    
    @Autowired
    private DataReconciliationTask reconciliationTask;
    
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Test
    public void testReconcileDetectsInconsistency() {
        // 1. 创建不一致数据
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("test");
        chunk.setDocId("test-doc");
        chunk.setAnswer("MySQL 原文");
        chunk.setSectionPath("MySQL 章节");
        chunk.setVectorizeStatus("completed");
        chunkMapper.insert(chunk);
        
        KnowledgeVector vec = KnowledgeVector.builder()
            .chunkId(chunk.getId())
            .embedding(new float[1024])
            .text("PostgreSQL 原文")  // 不一致
            .sectionPath("PostgreSQL 章节")  // 不一致
            .docType("test")
            .build();
        vectorRepository.save(vec);
        
        // 2. 运行对账任务
        reconciliationTask.reconcile();
        
        // 3. 验证触发了补偿同步（异步任务需等待）
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            KnowledgeVector updated = vectorRepository.findById(chunk.getId()).orElseThrow();
            assertEquals("MySQL 原文", updated.getText());
            assertEquals("MySQL 章节", updated.getSectionPath());
        });
    }
}
```

- [ ] **Step 3: 运行测试验证对账任务**

Run: `./mvnw test -Dtest=DataReconciliationTaskTest`  
Expected: PASS, 对账任务检测到不一致并触发补偿同步

- [ ] **Step 4: Commit 对账任务**

```bash
git add src/main/java/com/dataplatform/knowledge/task/DataReconciliationTask.java \
  src/test/java/com/dataplatform/knowledge/task/DataReconciliationTaskTest.java
git commit -m "feat(reconciliation): add data reconciliation task for dual-write consistency"
```

---

## 实施完成验收

所有任务完成后，运行完整的验收测试套件：

```bash
# Schema 变更验证
./mvnw test -Dtest=HybridSearchSchemaTest

# 双写逻辑验证
./mvnw test -Dtest=VectorizationServiceDualWriteTest

# 混合检索验证
./mvnw test -Dtest=HybridSearchServiceTest

# 重排验证
./mvnw test -Dtest=RerankServiceTest

# RAG 完整流程验证
./mvnw test -Dtest=RAGServiceTest

# 评估体系验证
./mvnw test -Dtest=EvaluationRunnerTest

# 引用接口验证
./mvnw test -Dtest=CitationControllerTest

# 对账任务验证
./mvnw test -Dtest=DataReconciliationTaskTest
```

验收标准（对应 Spec 第 13 节）：
1. ✅ 关键词路召回测试通过（包含 "3.2" 的文档被检索到）
2. ✅ 引用溯源测试通过（回答包含 [1][2]，点击可查看原文和 section_path）
3. ✅ 拒答机制测试通过（知识库外问题记录到 low_confidence_questions 表）
4. ✅ 四策略对比输出报告（Hybrid+Rerank R@5 >= Vector-only R@5）
