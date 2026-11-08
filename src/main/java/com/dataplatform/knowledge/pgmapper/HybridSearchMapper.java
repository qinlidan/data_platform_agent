package com.dataplatform.knowledge.pgmapper;

import com.dataplatform.knowledge.dto.Bm25Rows;
import com.dataplatform.knowledge.dto.ChunkCandidate;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 混合检索 Mapper（PostgreSQL + pgvector + pg_jieba）。
 * SQL 在 resources/mapper-pg/HybridSearchMapper.xml，绑定 postgresSqlSessionFactory（见 PostgresMyBatisConfig）。
 * 注意放在 pgmapper 包而不是 mapper 包：mapper 包会被默认的 MySQL 工厂扫描。
 */
@Mapper
public interface HybridSearchMapper {

    /**
     * 混合检索：向量路 + 关键词路 RRF 融合。
     *
     * @param queryVector pgvector 文本格式 "[0.1,0.2,...]"
     * @param queryText   原始查询文本（内部用 plainto_tsquery，任意用户输入都安全）
     * @param docType     文档类型过滤，null 或空串表示不过滤
     * @param topK        融合后返回条数
     */
    List<HybridSearchResult> hybridSearch(
            @Param("queryVector") String queryVector,
            @Param("queryText") String queryText,
            @Param("docType") String docType,
            @Param("topK") int topK);

    /** 纯向量检索（评估对比用） */
    List<HybridSearchResult> vectorSearch(
            @Param("queryVector") String queryVector,
            @Param("docType") String docType,
            @Param("topK") int topK);

    /** 纯关键词检索（评估对比用，ts_rank_cd 打分，AND 语义；不是 BM25） */
    List<HybridSearchResult> keywordSearch(
            @Param("queryText") String queryText,
            @Param("docType") String docType,
            @Param("topK") int topK);

    // ---------------- BM25 路（应用层打分，见 Bm25Scorer / HybridSearchService.bm25Search） ----------------

    /** 用 pg_jieba（jiebacfg）对查询文本分词，返回去重、去空白的词项。与建索引时同一套分词。 */
    List<String> queryTerms(@Param("text") String text);

    /** 全库统计：文档总数 N、平均文档长度 avgdl（词项数，不含空白词项）。全表扫描，调用方应缓存。 */
    Bm25Rows.CorpusStats corpusStats();

    /** 查询词项的文档频率 n（只返回库内出现过的词项）。 */
    List<Bm25Rows.TermDocFreq> docFrequencies(@Param("terms") List<String> terms);

    /** 候选召回：GIN 索引 + 词项 OR 匹配，按 ts_rank_cd 取前 limit 条，附带 docLength。 */
    List<Bm25Rows.Candidate> bm25Candidates(
            @Param("terms") List<String> terms,
            @Param("docType") String docType,
            @Param("limit") int limit);

    /** 候选 chunk 里每个查询词项的词频。 */
    List<Bm25Rows.TermFreq> termFrequencies(
            @Param("chunkIds") List<Long> chunkIds,
            @Param("terms") List<String> terms);

    /** 全部 chunk 的 id + 文档类型 + 章节路径（不含正文），评估时把 expect_section 解析成 chunk_id 用。 */
    List<ChunkCandidate> listAllChunkSections();

    /** 按关键词列出候选 chunk（给人工标注 groundTruth 用），text 截取前 120 字。 */
    List<ChunkCandidate> searchCandidates(
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
