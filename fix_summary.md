# 知识库管理修复总结

## 问题1: section_path字段为空

### 原因
`KnowledgeImportService.java` 中的 `importDocument` 方法在保存chunk时，没有从DTO复制 `sectionPath` 等字段到实体类。

### 修复
已修改 `src/main/java/com/dataplatform/service/KnowledgeImportService.java` 第117-132行，新增以下字段映射：
```java
chunk.setSymptomText(dto.getSymptomText());
chunk.setSectionPath(dto.getSectionPath());
chunk.setContentType(dto.getContentType());
chunk.setIsTunable(dto.getIsTunable());
chunk.setRouteAction(dto.getRouteAction());
```

### 验证步骤
1. 重启应用
2. 重新导入文档：`POST /api/knowledge/documents/{docId}/reimport`
3. 查询数据库确认：`SELECT id, section_path FROM knowledge_chunks LIMIT 5;`

---

## 问题2: 检索相似度显示NaN

### 可能原因
1. PostgreSQL未创建 `cosine_distance` 函数
2. 向量数据为NULL（向量化未完成）
3. pgvector扩展未正确安装

### 排查步骤
```sql
-- 1. 检查pgvector扩展
\dx

-- 2. 检查向量数据
SELECT chunk_id, doc_type, embedding IS NOT NULL as has_vector 
FROM knowledge_vectors LIMIT 5;

-- 3. 测试cosine_distance函数
SELECT cosine_distance('[1,0,0]'::vector, '[0,1,0]'::vector);
```

### 修复方案A: 使用<=>操作符（推荐）
修改 `KnowledgeVectorRepository.java` 第65行：
```java
// 原：1 - cosine_distance(embedding, CAST(:embedding AS vector)) AS score
// 改为：
1 - (embedding <=> CAST(:embedding AS vector)) AS score
```

### 修复方案B: 创建cosine_distance函数
```sql
CREATE OR REPLACE FUNCTION cosine_distance(vector, vector)
RETURNS float8 AS $$
  SELECT 1 - (($1 <#> $2) / (sqrt(($1 <#> $1)) * sqrt(($2 <#> $2))));
$$ LANGUAGE SQL IMMUTABLE STRICT;
```

---

## 额外修复: 自动导入逻辑

### 改进点
1. 从 `forEach` 改为普通 `for` 循环，确保顺序处理
2. 修正正则表达式：`\.m(ark)?d(own)?$` → `\.(md|markdown)$`
3. 增强日志：显示进度和统计信息
4. 向量化异常隔离：即使向量化失败，文档导入也能成功

### 日志输出示例
```
开始扫描 data/query 目录
找到 2 个文档文件
开始处理文档: spark调优.markdown
✓ 自动导入成功: spark调优.markdown (进度: 1/2)
开始处理文档: 数据研发平台用户手册.markdown
✓ 自动导入成功: 数据研发平台用户手册.markdown (进度: 2/2)
自动导入完成: 成功 2, 失败 0
```

---

## 待确认
1. 应用是否已重启并加载新代码
2. PostgreSQL中是否有向量数据
3. 向量化任务是否正常执行
