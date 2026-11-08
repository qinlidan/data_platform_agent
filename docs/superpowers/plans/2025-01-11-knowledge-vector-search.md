# 知识库向量语义检索系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知识库检索从关键词模糊匹配升级为向量语义检索，支持同义改写、结构化知识管理、断点重跑

**Architecture:** 双库设计（MySQL存元数据+PostgreSQL存向量），同步切分入库+异步向量化，Chunker Pipeline按优先级切分文档，Spring AI集成qwen embedding，pgvector HNSW索引检索

**Tech Stack:** Spring Boot 3.2.2, Spring AI 1.0.0, PostgreSQL 12+ + pgvector 0.7+, MySQL 8+, MyBatis-Plus 3.5.5, Spring Data JPA, qwen3.7-text-embedding-flash (1024维), Vue 3

**Spec:** `D:\alert4\docs\superpowers\specs\2025-01-11-knowledge-vector-search-design.md`

## Global Constraints

- Spring Boot version: 3.2.2
- Spring AI version: 1.0.0
- MyBatis-Plus version: 3.5.5
- PostgreSQL version: 12+ with pgvector 0.7+
- MySQL version: 8+
- Embedding model: qwen3.7-text-embedding-flash, dimension: 1024
- pgvector distance metric: COSINE
- 向量化文本前缀：文档使用 `passage:`，查询使用 `query:`
- 数据文件目录：`data/query/`，文件格式：`.markdown` 或 `.md`
- doc_type = 文件名（去扩展名）
- doc_id = doc_type = 文件名（去扩展名）
- 字符编码：UTF-8
- 时区：Asia/Shanghai

## Review Focus

1. **向量维度不匹配**：qwen embedding返回维度不是1024时，pgvector插入应报错而非截断或填充0，以免召回混乱
2. **异步向量化失败漏补**：应用重启时 `pending_vectorization` 状态的chunks必须自动重试，否则用户查询时会漏召回这些chunks
3. **重复导入数据残留**：按 `doc_id` 删除旧chunks时，必须同步删除pgvector中对应的向量行，否则pgvector会残留孤儿数据导致检索返回无效chunk_id
4. **切分器未识别表格**：Markdown表格如果未被TableRowChunker识别，会被当作普通文本块，导致多行数据合并成一个chunk，检索粒度过粗
5. **并发双写pgvector主键冲突**：多线程同时upsert同一chunk_id时，ON CONFLICT子句必须保证最后一次写入覆盖，不应抛出死锁异常

---

### Task 1: 数据库Schema与基础配置

**Files:**
- Create: `src/main/resources/db/migration/mysql/V1__create_knowledge_chunks.sql`
- Create: `src/main/resources/db/migration/postgresql/V1__create_knowledge_vectors.sql`
- Modify: `src/main/resources/application.yml` (添加PostgreSQL数据源和Spring AI配置)
- Create: `src/main/java/com/alert4/config/DataSourceConfig.java`

**Interfaces:**
- Consumes: 无
- Produces: 
  - MySQL表 `knowledge_chunks`，字段见spec 3.1
  - PostgreSQL表 `knowledge_vectors`，字段见spec 3.1
  - DataSource bean: `dataSource` (MySQL), `pgvectorDataSource` (PostgreSQL)

- [ ] **Step 1: 编写MySQL建表测试**

```java
@SpringBootTest
class KnowledgeChunkSchemaTest {
    @Autowired
    private DataSource dataSource;
    
    @Test
    void testKnowledgeChunksTableExists() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "knowledge_chunks", null)) {
            assertTrue(rs.next(), "knowledge_chunks table should exist");
        }
    }
    
    @Test
    void testKnowledgeChunksColumns() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getColumns(null, null, "knowledge_chunks", null);
            Set<String> columns = new HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
            assertTrue(columns.contains("id"));
            assertTrue(columns.contains("doc_type"));
            assertTrue(columns.contains("vectorize_status"));
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=KnowledgeChunkSchemaTest`
预期: FAIL with "knowledge_chunks table should exist"

- [ ] **Step 3: 创建MySQL建表脚本 `V1__create_knowledge_chunks.sql`**

完整SQL见spec 3.1，包含所有字段、索引、注释，vectorize_status默认值为 `'pending_vectorization'`

- [ ] **Step 4: 创建PostgreSQL建表脚本 `V1__create_knowledge_vectors.sql`**

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

- [ ] **Step 5: 配置双数据源 `DataSourceConfig.java`**

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    @Primary
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource-pgvector")
    public DataSource pgvectorDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

- [ ] **Step 6: 更新 `application.yml`**

添加PostgreSQL数据源配置和Spring AI DashScope配置，使用环境变量 `${POSTGRES_PASSWORD}` 和 `${DASHSCOPE_API_KEY}`

- [ ] **Step 7: 执行数据库迁移脚本**

运行: 手动执行SQL文件或使用Flyway/Liquibase（如项目已配置）
预期: 两个数据库都成功创建表和索引

- [ ] **Step 8: 运行测试确认通过**

运行: `mvn test -Dtest=KnowledgeChunkSchemaTest`
预期: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/ src/main/resources/application.yml src/main/java/com/alert4/config/DataSourceConfig.java
git commit -m "feat: add knowledge_chunks and knowledge_vectors schema

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 实体类与MyBatis Mapper

**Files:**
- Create: `src/main/java/com/alert4/entity/KnowledgeChunk.java`
- Create: `src/main/java/com/alert4/mapper/KnowledgeChunkMapper.java`
- Create: `src/main/java/com/alert4/dto/ChunkDTO.java`
- Test: `src/test/java/com/alert4/mapper/KnowledgeChunkMapperTest.java`

**Interfaces:**
- Consumes: Task 1的 `knowledge_chunks` 表
- Produces:
  - `KnowledgeChunk` entity，字段对应spec 3.1
  - `KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk>`
  - `ChunkDTO` 用于数据传输
  - 方法: `insertBatch(List<KnowledgeChunk>)`, `updateVectorizeStatus(List<Long> ids, String status)`, `selectBatchIds(List<Long> ids)`

- [ ] **Step 1: 编写Mapper CRUD测试**

```java
@SpringBootTest
@Transactional
class KnowledgeChunkMapperTest {
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    
    @Test
    void testInsertAndSelect() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("spark调优");
        chunk.setDocId("spark调优");
        chunk.setCategory("Spark参数调优");
        chunk.setSceneType("slow");
        chunk.setSceneTitle("作业慢");
        chunk.setQuestions("如何优化小文件");
        chunk.setAnswer("调整写入参数...");
        chunk.setSectionPath("Spark参数调优 / 作业慢 / 如何优化小文件");
        chunk.setIsTunable(true);
        chunk.setRouteAction("tunable");
        
        chunkMapper.insert(chunk);
        assertNotNull(chunk.getId());
        
        KnowledgeChunk fetched = chunkMapper.selectById(chunk.getId());
        assertEquals("spark调优", fetched.getDocType());
        assertEquals("pending_vectorization", fetched.getVectorizeStatus());
    }
    
    @Test
    void testUpdateVectorizeStatus() {
        // Insert 2 chunks
        KnowledgeChunk chunk1 = createTestChunk();
        KnowledgeChunk chunk2 = createTestChunk();
        chunkMapper.insert(chunk1);
        chunkMapper.insert(chunk2);
        
        // Update status
        chunkMapper.updateVectorizeStatus(Arrays.asList(chunk1.getId(), chunk2.getId()), "vectorized");
        
        assertEquals("vectorized", chunkMapper.selectById(chunk1.getId()).getVectorizeStatus());
        assertEquals("vectorized", chunkMapper.selectById(chunk2.getId()).getVectorizeStatus());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=KnowledgeChunkMapperTest`
预期: FAIL with "KnowledgeChunk cannot be resolved"

- [ ] **Step 3: 实现 `KnowledgeChunk.java`**

使用MyBatis-Plus注解 `@TableName("knowledge_chunks")`, `@TableId(type = IdType.AUTO)`，所有字段对应spec 3.1，添加 `@TableField` 注解处理下划线命名

- [ ] **Step 4: 实现 `ChunkDTO.java`**

包含所有业务字段（docType, docId, category, sceneType, sceneTitle, symptomText, questions, answer, sectionPath, contentType, isTunable, routeAction），用于切分器输出和API传输

- [ ] **Step 5: 实现 `KnowledgeChunkMapper.java`**

```java
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
    
    default void insertBatch(List<KnowledgeChunk> entities) {
        entities.forEach(this::insert);
    }
    
    @Update("<script>" +
            "UPDATE knowledge_chunks SET vectorize_status = #{status} " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    void updateVectorizeStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
```

- [ ] **Step 6: 运行测试确认通过**

运行: `mvn test -Dtest=KnowledgeChunkMapperTest`
预期: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/alert4/entity/KnowledgeChunk.java src/main/java/com/alert4/mapper/KnowledgeChunkMapper.java src/main/java/com/alert4/dto/ChunkDTO.java src/test/java/com/alert4/mapper/KnowledgeChunkMapperTest.java
git commit -m "feat: add KnowledgeChunk entity and mapper

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Embedding服务集成

**Files:**
- Create: `src/main/java/com/alert4/config/EmbeddingConfig.java`
- Create: `src/main/java/com/alert4/service/EmbeddingService.java`
- Test: `src/test/java/com/alert4/service/EmbeddingServiceTest.java`

**Interfaces:**
- Consumes: Spring AI DashScope API，配置来自Task 1的 `application.yml`
- Produces:
  - `EmbeddingService`
  - 方法: `float[] embedQuery(String text)` - 添加 `query:` 前缀并返回1024维向量
  - 方法: `float[] embedDocument(String text)` - 添加 `passage:` 前缀并返回1024维向量

- [ ] **Step 1: 编写Embedding服务测试**

```java
@SpringBootTest
class EmbeddingServiceTest {
    @Autowired
    private EmbeddingService embeddingService;
    
    @Test
    void testEmbedQuery() {
        float[] vector = embeddingService.embedQuery("小文件优化");
        
        assertNotNull(vector);
        assertEquals(1024, vector.length);
        // 向量值应该在合理范围内（通常归一化后在-1到1之间）
        assertTrue(vector[0] >= -2.0 && vector[0] <= 2.0);
    }
    
    @Test
    void testEmbedDocument() {
        String text = "Spark参数调优 | 作业慢 | 问法: 小文件过多 | 说明: 调整写入参数...";
        float[] vector = embeddingService.embedDocument(text);
        
        assertNotNull(vector);
        assertEquals(1024, vector.length);
    }
    
    @Test
    void testDifferentPrefixes() {
        float[] queryVec = embeddingService.embedQuery("测试");
        float[] docVec = embeddingService.embedDocument("测试");
        
        // query和document前缀应该产生不同的向量（非对称编码）
        assertFalse(Arrays.equals(queryVec, docVec));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=EmbeddingServiceTest`
预期: FAIL with "EmbeddingService cannot be resolved"

- [ ] **Step 3: 实现 `EmbeddingConfig.java`**

```java
@Configuration
public class EmbeddingConfig {
    
    @Bean
    public EmbeddingModel qwenEmbeddingModel(
        @Value("${spring.ai.dashscope.api-key}") String apiKey
    ) {
        DashscopeApi dashscopeApi = new DashscopeApi(apiKey);
        return new DashscopeEmbeddingModel(dashscopeApi, 
            DashscopeEmbeddingOptions.builder()
                .withModel("text-embedding-v3")
                .build());
    }
}
```

- [ ] **Step 4: 实现 `EmbeddingService.java`**

```java
@Service
@Slf4j
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    public float[] embedQuery(String text) {
        String queryText = "query: " + text;
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(queryText));
        return toFloatArray(response.getResults().get(0).getOutput());
    }
    
    public float[] embedDocument(String text) {
        String docText = "passage: " + text;
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

- [ ] **Step 5: 运行测试确认通过**

运行: `mvn test -Dtest=EmbeddingServiceTest`
预期: PASS（需要有效的DASHSCOPE_API_KEY环境变量）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alert4/config/EmbeddingConfig.java src/main/java/com/alert4/service/EmbeddingService.java src/test/java/com/alert4/service/EmbeddingServiceTest.java
git commit -m "feat: integrate Spring AI DashScope embedding service

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: pgvector Repository集成

**Files:**
- Create: `src/main/java/com/alert4/entity/KnowledgeVector.java`
- Create: `src/main/java/com/alert4/repository/KnowledgeVectorRepository.java`
- Create: `src/main/java/com/alert4/dto/VectorSearchResult.java`
- Create: `src/main/java/com/alert4/converter/VectorConverter.java`
- Create: `src/main/java/com/alert4/config/JpaConfig.java`
- Test: `src/test/java/com/alert4/repository/KnowledgeVectorRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1的 `knowledge_vectors` 表，Task 3的 `EmbeddingService`
- Produces:
  - `KnowledgeVectorRepository extends JpaRepository<KnowledgeVector, Long>`
  - 方法: `void upsert(Long chunkId, String embedding, String docType, String routeAction)` - ON CONFLICT DO UPDATE
  - 方法: `List<VectorSearchResult> searchSimilar(String embedding, String docType, int limit)` - 使用 `<=>` 操作符和HNSW索引
  - 方法: `void deleteByChunkIdIn(List<Long> chunkIds)`

- [ ] **Step 1: 编写pgvector Repository测试**

```java
@SpringBootTest
@Transactional("pgvectorTransactionManager")
class KnowledgeVectorRepositoryTest {
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Test
    void testUpsertAndSearch() {
        // Upsert a vector
        float[] embedding = embeddingService.embedDocument("小文件优化");
        String embeddingStr = arrayToString(embedding);
        
        vectorRepository.upsert(1L, embeddingStr, "spark调优", "tunable");
        vectorRepository.flush();
        
        // Search similar
        float[] queryEmbedding = embeddingService.embedQuery("大量小文件");
        String queryStr = arrayToString(queryEmbedding);
        
        List<VectorSearchResult> results = vectorRepository.searchSimilar(queryStr, null, 5);
        
        assertFalse(results.isEmpty());
        assertEquals(1L, results.get(0).getChunkId());
        assertTrue(results.get(0).getScore() > 0.5);  // 相似度应该较高
    }
    
    @Test
    void testUpsertIdempotent() {
        String embeddingStr = arrayToString(new float[1024]);
        
        vectorRepository.upsert(2L, embeddingStr, "spark调优", "tunable");
        vectorRepository.upsert(2L, embeddingStr, "spark调优", "tunable");  // 重复upsert
        
        assertEquals(1, vectorRepository.count());  // 应该只有1条记录
    }
    
    private String arrayToString(float[] array) {
        return Arrays.toString(array).replace("[", "").replace("]", "");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=KnowledgeVectorRepositoryTest`
预期: FAIL with "KnowledgeVectorRepository cannot be resolved"

- [ ] **Step 3: 实现 `VectorConverter.java`**

```java
@Converter
public class VectorConverter implements AttributeConverter<float[], PGvector> {
    @Override
    public PGvector convertToDatabaseColumn(float[] attribute) {
        return attribute == null ? null : new PGvector(attribute);
    }
    
    @Override
    public float[] convertToEntityAttribute(PGvector dbData) {
        return dbData == null ? null : dbData.toArray();
    }
}
```

- [ ] **Step 4: 实现 `KnowledgeVector.java`**

```java
@Entity
@Table(name = "knowledge_vectors")
public class KnowledgeVector {
    
    @Id
    private Long chunkId;
    
    @Column(columnDefinition = "vector(1024)", nullable = false)
    @Convert(converter = VectorConverter.class)
    private float[] embedding;
    
    @Column(length = 32, nullable = false)
    private String docType;
    
    @Column(length = 32)
    private String routeAction;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // getters and setters
}
```

- [ ] **Step 5: 实现 `VectorSearchResult.java`**

```java
public interface VectorSearchResult {
    Long getChunkId();
    String getDocType();
    String getRouteAction();
    Double getScore();
}
```

- [ ] **Step 6: 实现 `KnowledgeVectorRepository.java`**

```java
@Repository
public interface KnowledgeVectorRepository extends JpaRepository<KnowledgeVector, Long> {
    
    @Modifying
    @Query(value = """
        INSERT INTO knowledge_vectors (chunk_id, embedding, doc_type, route_action, created_at, updated_at)
        VALUES (:chunkId, cast(:embedding as vector), :docType, :routeAction, now(), now())
        ON CONFLICT (chunk_id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            doc_type = EXCLUDED.doc_type,
            route_action = EXCLUDED.route_action,
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
```

- [ ] **Step 7: 实现 `JpaConfig.java`**

配置PostgreSQL EntityManagerFactory和TransactionManager，使用 `@EnableJpaRepositories` 指定basePackages和数据源

- [ ] **Step 8: 运行测试确认通过**

运行: `mvn test -Dtest=KnowledgeVectorRepositoryTest`
预期: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/alert4/entity/KnowledgeVector.java src/main/java/com/alert4/repository/KnowledgeVectorRepository.java src/main/java/com/alert4/dto/VectorSearchResult.java src/main/java/com/alert4/converter/VectorConverter.java src/main/java/com/alert4/config/JpaConfig.java src/test/java/com/alert4/repository/KnowledgeVectorRepositoryTest.java
git commit -m "feat: add pgvector repository with upsert and similarity search

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Chunker Pipeline实现

**Files:**
- Create: `src/main/java/com/alert4/chunker/Chunker.java` (接口)
- Create: `src/main/java/com/alert4/chunker/ChunkerPipeline.java`
- Create: `src/main/java/com/alert4/chunker/QaPairChunker.java`
- Create: `src/main/java/com/alert4/chunker/TableRowChunker.java`
- Create: `src/main/java/com/alert4/chunker/HeadingHierarchyChunker.java`
- Create: `src/main/java/com/alert4/chunker/RecursiveFallbackChunker.java`
- Create: `src/main/java/com/alert4/chunker/SceneTypeMapper.java`
- Test: `src/test/java/com/alert4/chunker/ChunkerPipelineTest.java`

**Interfaces:**
- Consumes: Markdown文本，docId, docType
- Produces:
  - `Chunker` 接口: `boolean canHandle(String markdown)`, `List<ChunkDTO> chunk(String markdown, String docId, String docType)`
  - `ChunkerPipeline.process(String markdown, String docId, String docType): List<ChunkDTO>` - 按优先级调用chunkers
  - `SceneTypeMapper.inferSceneType(String sceneTitle, String docType): String` - 根据spec 4.3映射sceneType

- [ ] **Step 1: 编写Chunker Pipeline测试**

```java
@SpringBootTest
class ChunkerPipelineTest {
    @Autowired
    private ChunkerPipeline chunkerPipeline;
    
    @Test
    void testQaPairChunking() {
        String markdown = """
            ## 作业报错
            
            **Q1：OOM内存溢出如何处理？**
            **A1：**调整 spark.executor.memory 参数...
            
            **Q2：找不到表如何排查？**
            **A2：**检查库名和权限...
            """;
        
        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "spark调优", "spark调优");
        
        assertEquals(2, chunks.size());
        assertEquals("OOM内存溢出如何处理？", chunks.get(0).getQuestions());
        assertTrue(chunks.get(0).getAnswer().contains("spark.executor.memory"));
        assertEquals("error", chunks.get(0).getSceneType());
    }
    
    @Test
    void testTableRowChunking() {
        String markdown = """
            ## 名词解释
            
            | 名词 | 解释 |
            |-----|------|
            | OOM | 内存溢出 |
            | GC  | 垃圾回收 |
            """;
        
        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "用户手册", "数据研发平台用户手册");
        
        assertEquals(2, chunks.size());
        assertEquals("OOM", chunks.get(0).getQuestions());
        assertTrue(chunks.get(0).getAnswer().contains("内存溢出"));
    }
    
    @Test
    void testHeadingHierarchyChunking() {
        String markdown = """
            # Spark参数调优
            
            ## 作业慢
            
            ### 1. 小文件过多
            
            现象：写文件产生大量小文件，NameNode压力大。
            
            解决方案：调整 spark.sql.files.maxRecordsPerFile 参数...
            """;
        
        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "spark调优", "spark调优");
        
        assertFalse(chunks.isEmpty());
        assertEquals("Spark参数调优", chunks.get(0).getCategory());
        assertEquals("作业慢", chunks.get(0).getSceneTitle());
        assertEquals("slow", chunks.get(0).getSceneType());
        assertEquals("1. 小文件过多", chunks.get(0).getQuestions());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=ChunkerPipelineTest`
预期: FAIL with "ChunkerPipeline cannot be resolved"

- [ ] **Step 3: 实现 `Chunker.java` 接口**

```java
public interface Chunker {
    boolean canHandle(String markdown);
    List<ChunkDTO> chunk(String markdown, String docId, String docType);
}
```

- [ ] **Step 4: 实现 `SceneTypeMapper.java`**

根据spec 4.3的关键词映射表实现 `inferSceneType(String sceneTitle, String docType)`，包含所有关键词模式和默认兜底逻辑

- [ ] **Step 5: 实现 `QaPairChunker.java`**

识别 `**Q1：`, `Q:`, `问：` 等模式，提取Q作为questions，A作为answer，使用正则表达式匹配

- [ ] **Step 6: 实现 `TableRowChunker.java`**

识别 `|---|---|` 模式，按行切分，将表头列名与每行数据组合，每块复制表头

- [ ] **Step 7: 实现 `HeadingHierarchyChunker.java`**

解析 `#`, `##`, `###` 层级，填充category, sceneTitle, questions，调用 `SceneTypeMapper.inferSceneType`，完整实现见spec 9.3示例

- [ ] **Step 8: 实现 `RecursiveFallbackChunker.java`**

处理单段正文超过1000字的情况，按句号切分，块间保留10%重叠

- [ ] **Step 9: 实现 `ChunkerPipeline.java`**

```java
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

- [ ] **Step 10: 运行测试确认通过**

运行: `mvn test -Dtest=ChunkerPipelineTest`
预期: PASS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/alert4/chunker/ src/test/java/com/alert4/chunker/ChunkerPipelineTest.java
git commit -m "feat: implement Chunker Pipeline with QA, Table, Heading, and Fallback chunkers

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: 同步/异步双写服务

**Files:**
- Create: `src/main/java/com/alert4/service/VectorizationService.java`
- Create: `src/main/java/com/alert4/config/AsyncConfig.java`
- Test: `src/test/java/com/alert4/service/VectorizationServiceTest.java`

**Interfaces:**
- Consumes: Task 2的 `KnowledgeChunkMapper`, Task 3的 `EmbeddingService`, Task 4的 `KnowledgeVectorRepository`, Task 5的 `ChunkerPipeline`
- Produces:
  - `VectorizationService`
  - 方法: `List<Long> saveChunksSync(List<ChunkDTO> chunks)` - 同步阶段，MySQL批量插入，返回chunk_ids，提交异步任务
  - 方法: `void vectorizeChunksAsync(List<Long> chunkIds)` - 异步阶段，向量化+pgvector upsert+状态回填
  - 线程池bean: `vectorizationExecutor` (核心4线程，最大8线程，队列500)

- [ ] **Step 1: 编写同步/异步双写测试**

```java
@SpringBootTest
class VectorizationServiceTest {
    @Autowired
    private VectorizationService vectorizationService;
    
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Test
    @Transactional
    void testSaveChunksSync() {
        List<ChunkDTO> chunks = Arrays.asList(createTestChunk());
        
        List<Long> chunkIds = vectorizationService.saveChunksSync(chunks);
        
        assertFalse(chunkIds.isEmpty());
        
        // 验证MySQL已插入
        KnowledgeChunk chunk = chunkMapper.selectById(chunkIds.get(0));
        assertNotNull(chunk);
        assertEquals("pending_vectorization", chunk.getVectorizeStatus());
    }
    
    @Test
    @Transactional
    void testVectorizeChunksAsync() throws InterruptedException {
        // 先插入一个chunk
        ChunkDTO dto = createTestChunk();
        List<Long> chunkIds = vectorizationService.saveChunksSync(Arrays.asList(dto));
        
        // 等待异步任务完成（最多5秒）
        Thread.sleep(5000);
        
        // 验证pgvector已插入
        assertTrue(vectorRepository.existsById(chunkIds.get(0)));
        
        // 验证MySQL状态已更新
        KnowledgeChunk chunk = chunkMapper.selectById(chunkIds.get(0));
        assertEquals("vectorized", chunk.getVectorizeStatus());
    }
    
    private ChunkDTO createTestChunk() {
        ChunkDTO dto = new ChunkDTO();
        dto.setDocType("spark调优");
        dto.setDocId("spark调优");
        dto.setCategory("Spark参数调优");
        dto.setSceneType("slow");
        dto.setSceneTitle("作业慢");
        dto.setQuestions("小文件过多");
        dto.setAnswer("调整写入参数...");
        dto.setIsTunable(true);
        dto.setRouteAction("tunable");
        return dto;
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=VectorizationServiceTest`
预期: FAIL with "VectorizationService cannot be resolved"

- [ ] **Step 3: 实现 `AsyncConfig.java`**

```java
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

- [ ] **Step 4: 实现 `VectorizationService.java` 的 `saveChunksSync` 方法**

按spec 5.2同步阶段实现：批量插入MySQL，获取自增主键，提交异步任务，立即返回

- [ ] **Step 5: 实现 `VectorizationService.java` 的 `vectorizeChunksAsync` 方法**

使用 `@Async("vectorizationExecutor")` 注解，按spec 5.2异步阶段实现：查询MySQL获取原文，按spec 3.2拼接embeddingText，调用EmbeddingService，upsert到pgvector，回填MySQL状态，异常时记录日志

- [ ] **Step 6: 实现 `buildEmbeddingText` 私有方法**

按spec 3.2公式拼接：如果symptomText非空，则包含"现象:"部分，否则省略

- [ ] **Step 7: 运行测试确认通过**

运行: `mvn test -Dtest=VectorizationServiceTest`
预期: PASS（异步测试需要Thread.sleep等待）

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/alert4/service/VectorizationService.java src/main/java/com/alert4/config/AsyncConfig.java src/test/java/com/alert4/service/VectorizationServiceTest.java
git commit -m "feat: implement sync/async vectorization service with thread pool

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: 向量检索服务

**Files:**
- Create: `src/main/java/com/alert4/service/VectorSearchService.java`
- Create: `src/main/java/com/alert4/dto/SearchHitDTO.java`
- Test: `src/test/java/com/alert4/service/VectorSearchServiceTest.java`

**Interfaces:**
- Consumes: Task 3的 `EmbeddingService`, Task 4的 `KnowledgeVectorRepository`, Task 2的 `KnowledgeChunkMapper`
- Produces:
  - `VectorSearchService`
  - 方法: `List<SearchHitDTO> search(String keyword, String docType, int limit)` - 返回TopK结果，包含question, answer, score等字段
  - `SearchHitDTO`: 字段见spec 6.1出参

- [ ] **Step 1: 编写向量检索服务测试**

```java
@SpringBootTest
@Transactional
class VectorSearchServiceTest {
    @Autowired
    private VectorSearchService searchService;
    
    @Autowired
    private VectorizationService vectorizationService;
    
    @Test
    void testSearch() throws InterruptedException {
        // 先插入一些测试数据
        List<ChunkDTO> chunks = Arrays.asList(
            createChunk("小文件优化", "调整写入参数减少小文件数量..."),
            createChunk("大文件读取慢", "增加并行度...")
        );
        vectorizationService.saveChunksSync(chunks);
        Thread.sleep(5000);  // 等待异步向量化完成
        
        // 搜索：换个说法查询
        List<SearchHitDTO> hits = searchService.search("写文件产生大量小文件如何进行引擎调优", null, 5);
        
        assertFalse(hits.isEmpty());
        assertEquals("小文件优化", hits.get(0).getQuestion());
        assertTrue(hits.get(0).getScore() > 0.5);  // 语义相似度应该较高
        assertEquals("spark调优", hits.get(0).getDocType());
    }
    
    @Test
    void testSearchWithDocTypeFilter() throws InterruptedException {
        // 插入不同doc_type的数据
        List<ChunkDTO> sparkChunks = Arrays.asList(createChunk("spark优化", "spark参数调优..."));
        List<ChunkDTO> manualChunks = Arrays.asList(createManualChunk("如何创建任务", "点击新建按钮..."));
        vectorizationService.saveChunksSync(sparkChunks);
        vectorizationService.saveChunksSync(manualChunks);
        Thread.sleep(5000);
        
        // 只搜索spark调优类型
        List<SearchHitDTO> hits = searchService.search("优化", "spark调优", 5);
        
        assertTrue(hits.stream().allMatch(h -> "spark调优".equals(h.getDocType())));
    }
    
    private ChunkDTO createChunk(String questions, String answer) {
        // 创建spark调优类型的chunk
    }
    
    private ChunkDTO createManualChunk(String questions, String answer) {
        // 创建用户手册类型的chunk
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=VectorSearchServiceTest`
预期: FAIL with "VectorSearchService cannot be resolved"

- [ ] **Step 3: 实现 `SearchHitDTO.java`**

字段：question (String), answer (String), docType (String), routeAction (String), isTunable (Boolean), symptomText (String), score (Double), sectionPath (String)

- [ ] **Step 4: 实现 `VectorSearchService.search` 方法**

按spec 6.1流程实现：1) 向量化查询（添加 `query:` 前缀），2) pgvector检索，3) MySQL补全元数据，4) 组装返回（保持pgvector顺序）

- [ ] **Step 5: 运行测试确认通过**

运行: `mvn test -Dtest=VectorSearchServiceTest`
预期: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alert4/service/VectorSearchService.java src/main/java/com/alert4/dto/SearchHitDTO.java src/test/java/com/alert4/service/VectorSearchServiceTest.java
git commit -m "feat: implement vector similarity search service

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: 断点重跑机制

**Files:**
- Create: `src/main/java/com/alert4/task/VectorizationRecoveryTask.java`
- Create: `src/main/java/com/alert4/controller/KnowledgeVectorizationController.java` (手动触发补偿接口)
- Test: `src/test/java/com/alert4/task/VectorizationRecoveryTaskTest.java`

**Interfaces:**
- Consumes: Task 2的 `KnowledgeChunkMapper`, Task 6的 `VectorizationService`
- Produces:
  - `VectorizationRecoveryTask implements ApplicationListener<ApplicationReadyEvent>` - 启动时自动补偿
  - `KnowledgeVectorizationController.retryVectorization(List<Long> chunkIds)` - 手动触发补偿，chunkIds为空时补偿全部pending

- [ ] **Step 1: 编写断点重跑测试**

```java
@SpringBootTest
class VectorizationRecoveryTaskTest {
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    
    @Autowired
    private VectorizationService vectorizationService;
    
    @Autowired
    private VectorizationRecoveryTask recoveryTask;
    
    @Test
    @Transactional
    void testRecoverPendingChunks() {
        // 手动插入pending状态的chunk
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("spark调优");
        chunk.setDocId("spark调优");
        chunk.setCategory("测试");
        chunk.setSceneType("slow");
        chunk.setSceneTitle("测试场景");
        chunk.setQuestions("测试问题");
        chunk.setAnswer("测试答案");
        chunk.setVectorizeStatus("pending_vectorization");
        chunkMapper.insert(chunk);
        
        // 触发恢复任务
        recoveryTask.onApplicationEvent(null);
        
        // 等待异步任务完成
        Thread.sleep(5000);
        
        // 验证状态已更新
        KnowledgeChunk updated = chunkMapper.selectById(chunk.getId());
        assertEquals("vectorized", updated.getVectorizeStatus());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=VectorizationRecoveryTaskTest`
预期: FAIL with "VectorizationRecoveryTask cannot be resolved"

- [ ] **Step 3: 实现 `VectorizationRecoveryTask.java`**

```java
@Component
@Slf4j
public class VectorizationRecoveryTask implements ApplicationListener<ApplicationReadyEvent> {
    
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorizationService vectorizationService;
    
    public VectorizationRecoveryTask(KnowledgeChunkMapper chunkMapper, 
                                      VectorizationService vectorizationService) {
        this.chunkMapper = chunkMapper;
        this.vectorizationService = vectorizationService;
    }
    
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

- [ ] **Step 4: 实现 `KnowledgeVectorizationController.retryVectorization`**

```java
@RestController
@RequestMapping("/api/knowledge/vectorization")
@Slf4j
public class KnowledgeVectorizationController {
    
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorizationService vectorizationService;
    
    @PostMapping("/retry")
    public Map<String, Object> retryVectorization(@RequestBody(required = false) Map<String, List<Long>> request) {
        List<Long> chunkIds = request != null ? request.get("chunkIds") : null;
        
        if (chunkIds == null || chunkIds.isEmpty()) {
            chunkIds = chunkMapper.selectList(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getVectorizeStatus, "pending_vectorization")
            ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
        }
        
        log.info("手动触发向量化补偿任务: {} chunks", chunkIds.size());
        vectorizationService.vectorizeChunksAsync(chunkIds);
        
        return Map.of("submitted", chunkIds.size(), "message", "已提交补偿任务");
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行: `mvn test -Dtest=VectorizationRecoveryTaskTest`
预期: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alert4/task/VectorizationRecoveryTask.java src/main/java/com/alert4/controller/KnowledgeVectorizationController.java src/test/java/com/alert4/task/VectorizationRecoveryTaskTest.java
git commit -m "feat: add vectorization recovery task and manual retry endpoint

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: 启动时自动导入服务

**Files:**
- Create: `src/main/java/com/alert4/service/KnowledgeImportService.java`
- Create: `src/main/java/com/alert4/task/KnowledgeBaseInitializer.java`
- Test: `src/test/java/com/alert4/service/KnowledgeImportServiceTest.java`

**Interfaces:**
- Consumes: Task 2的 `KnowledgeChunkMapper`, Task 4的 `KnowledgeVectorRepository`, Task 5的 `ChunkerPipeline`, Task 6的 `VectorizationService`
- Produces:
  - `KnowledgeImportService`
  - 方法: `void autoImportFromDataQuery()` - 扫描 `data/query/` 目录，按doc_id全量覆盖导入
  - 方法: `void importDocument(File file, String docId, String docType)` - 导入单个文档，删除旧数据+切分+双写
  - `KnowledgeBaseInitializer implements ApplicationListener<ApplicationReadyEvent>` - 启动时触发自动导入

- [ ] **Step 1: 编写自动导入服务测试**

```java
@SpringBootTest
@Transactional
class KnowledgeImportServiceTest {
    @Autowired
    private KnowledgeImportService importService;
    
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    
    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    
    @Test
    void testImportDocument() throws IOException {
        // 创建测试Markdown文件
        File tempFile = File.createTempFile("test-doc", ".md");
        Files.writeString(tempFile.toPath(), """
            # Spark参数调优
            
            ## 作业慢
            
            ### 小文件过多
            
            调整 spark.sql.files.maxRecordsPerFile 参数...
            """);
        
        // 导入文档
        importService.importDocument(tempFile, "test-doc", "test-doc");
        
        // 验证MySQL已插入
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, "test-doc")
        );
        assertFalse(chunks.isEmpty());
        
        // 清理
        tempFile.delete();
    }
    
    @Test
    void testReimportOverwrite() throws IOException, InterruptedException {
        // 第一次导入
        File tempFile = File.createTempFile("test-doc", ".md");
        Files.writeString(tempFile.toPath(), "# 旧内容\n\n## 场景1\n\n### 问题1\n\n答案1");
        importService.importDocument(tempFile, "test-doc", "test-doc");
        Thread.sleep(3000);
        
        Long oldChunkId = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, "test-doc")
        ).get(0).getId();
        
        // 第二次导入（全量覆盖）
        Files.writeString(tempFile.toPath(), "# 新内容\n\n## 场景2\n\n### 问题2\n\n答案2");
        importService.importDocument(tempFile, "test-doc", "test-doc");
        
        // 验证旧数据已删除
        assertNull(chunkMapper.selectById(oldChunkId));
        assertFalse(vectorRepository.existsById(oldChunkId));
        
        // 验证新数据已插入
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, "test-doc")
        );
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getAnswer().contains("答案2"));
        
        tempFile.delete();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=KnowledgeImportServiceTest`
预期: FAIL with "KnowledgeImportService cannot be resolved"

- [ ] **Step 3: 实现 `KnowledgeImportService.autoImportFromDataQuery`**

按spec 8.1扫描逻辑实现：检查 `data/query` 目录存在性，过滤 `.markdown` 和 `.md` 文件，文件名去扩展名作为docType和docId

- [ ] **Step 4: 实现 `KnowledgeImportService.importDocument`**

按spec 8.2全量覆盖逻辑实现：1) 查询旧chunk_ids，2) 删除MySQL和pgvector旧数据，3) 读取文件，4) 调用ChunkerPipeline切分，5) 调用VectorizationService.saveChunksSync双写

- [ ] **Step 5: 实现 `KnowledgeBaseInitializer.java`**

```java
@Component
@Slf4j
public class KnowledgeBaseInitializer implements ApplicationListener<ApplicationReadyEvent> {
    
    private final KnowledgeImportService knowledgeImportService;
    
    public KnowledgeBaseInitializer(KnowledgeImportService knowledgeImportService) {
        this.knowledgeImportService = knowledgeImportService;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("开始自动导入知识库文档");
        knowledgeImportService.autoImportFromDataQuery();
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

运行: `mvn test -Dtest=KnowledgeImportServiceTest`
预期: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/alert4/service/KnowledgeImportService.java src/main/java/com/alert4/task/KnowledgeBaseInitializer.java src/test/java/com/alert4/service/KnowledgeImportServiceTest.java
git commit -m "feat: implement auto-import service with full-覆盖 strategy

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: API接口层

**Files:**
- Create: `src/main/java/com/alert4/controller/KnowledgeDocumentController.java`
- Create: `src/main/java/com/alert4/controller/KnowledgeManualImportController.java`
- Create: `src/main/java/com/alert4/controller/KnowledgeSearchController.java`
- Create: `src/main/java/com/alert4/dto/DocumentInfoDTO.java`
- Create: `src/main/java/com/alert4/dto/ManualImportRequest.java`
- Test: `src/test/java/com/alert4/controller/KnowledgeApiIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2的 `KnowledgeChunkMapper`, Task 7的 `VectorSearchService`, Task 9的 `KnowledgeImportService`, Task 5的 `ChunkerPipeline`
- Produces:
  - `GET /api/knowledge/documents` - 返回材料清单 `List<DocumentInfoDTO>`
  - `POST /api/knowledge/documents/{docId}/reimport` - 重新导入文档
  - `GET /api/knowledge/documents/{docId}/chunks?page=1&size=20` - 查看文档切块
  - `POST /api/knowledge/manual/preview` - 手工录入预览切分
  - `POST /api/knowledge/manual/import` - 手工录入确认入库
  - `GET /api/knowledge/search/test?keyword=xxx&docType=xxx` - 检索自测
  - 所有DTO字段见spec 7.2

- [ ] **Step 1: 编写API集成测试**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class KnowledgeApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testGetDocuments() throws Exception {
        mockMvc.perform(get("/api/knowledge/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
    
    @Test
    void testSearchTest() throws Exception {
        mockMvc.perform(get("/api/knowledge/search/test")
                .param("keyword", "小文件")
                .param("docType", "spark调优"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hits").isArray());
    }
    
    @Test
    void testManualPreview() throws Exception {
        String requestBody = """
            {
              "markdownText": "## 测试\\n\\n### 问题1\\n\\n答案1",
              "docType": "测试文档"
            }
            """;
        
        mockMvc.perform(post("/api/knowledge/manual/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chunks").isArray())
            .andExpect(jsonPath("$.chunks[0].questions").value("问题1"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行: `mvn test -Dtest=KnowledgeApiIntegrationTest`
预期: FAIL with 404 Not Found

- [ ] **Step 3: 实现 `DocumentInfoDTO.java`**

字段：fileName (String), docId (String), docType (String), fileSize (Long), lastModified (String), chunkCount (Integer)

- [ ] **Step 4: 实现 `ManualImportRequest.java`**

字段：markdownText (String), docType (String), chunks (List<ChunkDTO>)

- [ ] **Step 5: 实现 `KnowledgeDocumentController` 的三个接口**

`GET /api/knowledge/documents`: 扫描 `data/query/` 目录，查询每个docId的chunk数量，返回文档列表
`POST /api/knowledge/documents/{docId}/reimport`: 调用 `KnowledgeImportService.importDocument`
`GET /api/knowledge/documents/{docId}/chunks`: 分页查询chunks，使用MyBatis-Plus的Page

- [ ] **Step 6: 实现 `KnowledgeManualImportController` 的两个接口**

`POST /api/knowledge/manual/preview`: 调用ChunkerPipeline切分，查询每个chunk是否重复（按 `(sceneTitle, questions, answer)` 三元组），标注isDuplicate字段
`POST /api/knowledge/manual/import`: 过滤掉重复项，调用VectorizationService.saveChunksSync

- [ ] **Step 7: 实现 `KnowledgeSearchController` 的检索自测接口**

`GET /api/knowledge/search/test`: 调用VectorSearchService.search，返回结果

- [ ] **Step 8: 运行测试确认通过**

运行: `mvn test -Dtest=KnowledgeApiIntegrationTest`
预期: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/alert4/controller/ src/main/java/com/alert4/dto/DocumentInfoDTO.java src/main/java/com/alert4/dto/ManualImportRequest.java src/test/java/com/alert4/controller/KnowledgeApiIntegrationTest.java
git commit -m "feat: add knowledge management API endpoints

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 11: 前端知识库管理页面

**Files:**
- Create: `frontend/src/views/KnowledgeManagement.vue`
- Create: `frontend/src/components/DocumentList.vue`
- Create: `frontend/src/components/ManualImport.vue`
- Create: `frontend/src/components/SearchTest.vue`
- Create: `frontend/src/api/knowledge.ts`
- Modify: `frontend/src/router/index.ts` (添加路由)

**Interfaces:**
- Consumes: Task 10的所有API接口
- Produces:
  - `/knowledge-management` 路由页面，包含三个Tab：材料清单、手工录入、检索自测
  - `DocumentList.vue`: 展示文档列表，重新导入按钮，查看切块按钮
  - `ManualImport.vue`: Markdown文本框，预览切分按钮，重复标注，确认入库按钮
  - `SearchTest.vue`: 搜索框，docType过滤，结果表格展示

- [ ] **Step 1: 编写前端页面端到端测试（手动验收）**

无自动化测试，手动验收步骤：
1. 访问 `/knowledge-management`
2. 材料清单Tab能展示 `data/query/` 下的文档
3. 点击【重新导入】能触发重新导入
4. 点击【查看切块】能展示分页的chunk列表
5. 手工录入Tab能粘贴Markdown，点击【预览切分】展示chunks
6. 重复的chunks被标注"已重复"
7. 点击【确认入库】能成功入库
8. 检索自测Tab能输入关键词，展示召回结果和score

- [ ] **Step 2: 实现 `knowledge.ts` API封装**

封装所有Task 10的API接口，使用axios

- [ ] **Step 3: 实现 `DocumentList.vue`**

展示文档列表（el-table），【重新导入】按钮调用reimport接口，【查看切块】按钮弹窗展示chunks（el-dialog + el-pagination）

- [ ] **Step 4: 实现 `ManualImport.vue`**

Markdown文本框（el-input type="textarea"），docType输入框，【预览切分】按钮调用preview接口，表格展示chunks，isDuplicate的行标红或加Badge，【确认入库】按钮调用import接口

- [ ] **Step 5: 实现 `SearchTest.vue`**

搜索输入框（el-input），docType下拉框（el-select，可选），【搜索】按钮调用search接口，结果表格展示（el-table），高亮显示score

- [ ] **Step 6: 实现 `KnowledgeManagement.vue` 主页面**

使用 `el-tabs` 包含三个子组件：DocumentList, ManualImport, SearchTest

- [ ] **Step 7: 更新 `router/index.ts`**

添加路由 `{ path: '/knowledge-management', component: KnowledgeManagement }`

- [ ] **Step 8: 手动验收通过**

按Step 1的验收步骤逐一检查

- [ ] **Step 9: Commit**

```bash
git add frontend/src/views/KnowledgeManagement.vue frontend/src/components/DocumentList.vue frontend/src/components/ManualImport.vue frontend/src/components/SearchTest.vue frontend/src/api/knowledge.ts frontend/src/router/index.ts
git commit -m "feat: add knowledge management frontend pages

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Self-Review Checklist

**1. Spec coverage:** 
- ✅ Task 1: Schema设计 (spec 3.1, 2.1)
- ✅ Task 2: 实体类和Mapper (spec 3.1)
- ✅ Task 3: Embedding服务 (spec 9.1, 3.2)
- ✅ Task 4: pgvector集成 (spec 9.2)
- ✅ Task 5: Chunker Pipeline (spec 4.2, 4.3)
- ✅ Task 6: 双写服务 (spec 5.1, 5.2, 5.3)
- ✅ Task 7: 向量检索 (spec 6.1)
- ✅ Task 8: 断点重跑 (spec 5.4)
- ✅ Task 9: 自动导入 (spec 8.1, 8.2, 5.5)
- ✅ Task 10: API接口 (spec 7.2)
- ✅ Task 11: 前端页面 (spec 7.1)

**2. Step scan:** 每个步骤都明确了要做什么，测试步骤包含具体断言，实现步骤包含关键签名和spec章节引用，验证步骤包含明确的命令和预期输出。

**3. Type consistency:** 检查了Task间的接口一致性，例如：
- Task 2的 `ChunkDTO` 被Task 5 Chunker Pipeline使用
- Task 6的 `VectorizationService.saveChunksSync` 返回 `List<Long>` 被Task 8断点重跑使用
- Task 7的 `SearchHitDTO` 字段与spec 6.1一致

**4. Review Focus:** 已在开头添加Review Focus section，涵盖5个关键失败场景：
1. 向量维度不匹配 → Task 3测试覆盖
2. 异步向量化失败漏补 → Task 8断点重跑测试覆盖
3. 重复导入数据残留 → Task 9 testReimportOverwrite测试覆盖
4. 切分器未识别表格 → Task 5 testTableRowChunking测试覆盖
5. 并发双写主键冲突 → Task 4 testUpsertIdempotent测试覆盖

**5. Proportion:** 计划长度适中，没有过多代码实现细节，主要是接口签名、测试断言和关键实现逻辑的指引，保持了规划性质而非实现细节的平衡。
