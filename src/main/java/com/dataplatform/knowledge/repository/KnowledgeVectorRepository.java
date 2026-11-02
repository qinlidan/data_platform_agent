package com.dataplatform.knowledge.repository;

import com.dataplatform.knowledge.dto.VectorSearchResult;
import com.dataplatform.knowledge.entity.KnowledgeVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * pgvector Repository - 向量相似度检索
 * 使用 COSINE 距离度量 (<=> 操作符)
 *
 * Task 4 - Step 6
 */
@Repository
public interface KnowledgeVectorRepository extends JpaRepository<KnowledgeVector, Long> {

    /**
     * Upsert向量（ON CONFLICT UPDATE）
     * 处理并发upsert时的主键冲突
     *
     * @param chunkId chunk ID
     * @param embedding 向量字符串，格式：[0.1,0.2,...]
     * @param docType 文档类型
     * @param routeAction 路由动作
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO knowledge_vectors (chunk_id, embedding, doc_type, route_action, created_at, updated_at)
        VALUES (:chunkId, CAST(:embedding AS vector), :docType, :routeAction, :createdAt, :updatedAt)
        ON CONFLICT (chunk_id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            doc_type = EXCLUDED.doc_type,
            route_action = EXCLUDED.route_action,
            updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("chunkId") Long chunkId,
                @Param("embedding") String embedding,
                @Param("docType") String docType,
                @Param("routeAction") String routeAction,
                @Param("createdAt") LocalDateTime createdAt,
                @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 向量相似度检索（使用 COSINE 距离）
     * 使用函数形式 cosine_distance() 以兼容 JSqlParser
     * 返回 top-k 最相似的 chunk_id
     *
     * @param embedding 查询向量字符串，格式：[0.1,0.2,...]
     * @param docType 文档类型过滤（null表示不过滤）
     * @param limit 返回top-k结果数量
     * @return 按相似度降序排列的结果列表
     */
    @Query(value = """
        SELECT chunk_id AS chunkId,
               doc_type AS docType,
               route_action AS routeAction,
               1 - cosine_distance(embedding, CAST(:embedding AS vector)) AS score
        FROM knowledge_vectors
        WHERE (:docType IS NULL OR doc_type = :docType)
        ORDER BY cosine_distance(embedding, CAST(:embedding AS vector))
        LIMIT :limit
        """, nativeQuery = true)
    List<VectorSearchResult> searchSimilar(@Param("embedding") String embedding,
                                           @Param("docType") String docType,
                                           @Param("limit") int limit);

    /**
     * 批量删除向量（用于重新导入时清理旧数据）
     *
     * @param chunkIds chunk ID列表
     */
    @Modifying
    @Query(value = "DELETE FROM knowledge_vectors WHERE chunk_id IN :chunkIds", nativeQuery = true)
    void deleteByChunkIdIn(@Param("chunkIds") List<Long> chunkIds);

    /**
     * 封装 upsert 方法，自动填充时间戳
     *
     * @param chunkId chunk ID
     * @param embedding 向量字符串
     * @param docType 文档类型
     * @param routeAction 路由动作
     */
    default void upsert(Long chunkId, String embedding, String docType, String routeAction) {
        LocalDateTime now = LocalDateTime.now();
        upsert(chunkId, embedding, docType, routeAction, now, now);
    }
}
