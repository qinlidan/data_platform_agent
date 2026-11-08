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
     * Upsert向量（ON CONFLICT UPDATE）- 包含混合检索字段
     * 处理并发upsert时的主键冲突
     *
     * @param chunkId chunk ID
     * @param embedding 向量字符串，格式：[0.1,0.2,...]
     * @param docType 文档类型
     * @param routeAction 路由动作
     * @param text 原文（用于全文检索）
     * @param sectionPath 章节路径（用于引用溯源）
     * @param sceneType 场景类型（用于精排）
     * @param sceneTitle 场景标题（用于精排）
     * @param symptomText 症状描述（用于精排）
     * @param questions 问题列表（用于精排）
     * @param answer 答案内容（用于精排）
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO knowledge_vectors (
            chunk_id, embedding, doc_type, route_action,
            text, section_path, scene_type, scene_title, symptom_text, questions, answer,
            created_at, updated_at
        )
        VALUES (
            :chunkId, CAST(:embedding AS vector), :docType, :routeAction,
            :text, :sectionPath, :sceneType, :sceneTitle, :symptomText, :questions, :answer,
            :createdAt, :updatedAt
        )
        ON CONFLICT (chunk_id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            doc_type = EXCLUDED.doc_type,
            route_action = EXCLUDED.route_action,
            text = EXCLUDED.text,
            section_path = EXCLUDED.section_path,
            scene_type = EXCLUDED.scene_type,
            scene_title = EXCLUDED.scene_title,
            symptom_text = EXCLUDED.symptom_text,
            questions = EXCLUDED.questions,
            answer = EXCLUDED.answer,
            updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsertWithHybridFields(@Param("chunkId") Long chunkId,
                @Param("embedding") String embedding,
                @Param("docType") String docType,
                @Param("routeAction") String routeAction,
                @Param("text") String text,
                @Param("sectionPath") String sectionPath,
                @Param("sceneType") String sceneType,
                @Param("sceneTitle") String sceneTitle,
                @Param("symptomText") String symptomText,
                @Param("questions") String questions,
                @Param("answer") String answer,
                @Param("createdAt") LocalDateTime createdAt,
                @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 向量化双写专用 upsert：同时写入 embedding、text、rerank_text、section_path。
     * text_tsv 是生成列，由数据库根据 text 自动维护，这里不写。
     *
     * @param text        全文检索字段（doc_type + scene_title + questions + answer）
     * @param rerankText  精排字段（scene_title + questions + answer，不含 doc_type）
     * @param sectionPath 章节路径（引用溯源）
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO knowledge_vectors (
            chunk_id, embedding, doc_type, route_action,
            text, rerank_text, section_path,
            created_at, updated_at
        )
        VALUES (
            :chunkId, CAST(:embedding AS vector), :docType, :routeAction,
            :text, :rerankText, :sectionPath,
            :createdAt, :updatedAt
        )
        ON CONFLICT (chunk_id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            doc_type = EXCLUDED.doc_type,
            route_action = EXCLUDED.route_action,
            text = EXCLUDED.text,
            rerank_text = EXCLUDED.rerank_text,
            section_path = EXCLUDED.section_path,
            updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("chunkId") Long chunkId,
                @Param("embedding") String embedding,
                @Param("docType") String docType,
                @Param("routeAction") String routeAction,
                @Param("text") String text,
                @Param("rerankText") String rerankText,
                @Param("sectionPath") String sectionPath,
                @Param("createdAt") LocalDateTime createdAt,
                @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Upsert向量（ON CONFLICT UPDATE）- 遗留方法（向后兼容）
     * 只写 embedding，不写 text/rerank_text/section_path，向量化路径不要再用它。
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
     * 封装 upsert 方法，自动填充时间戳（向后兼容）
     * 新代码应使用 upsertWithHybridFields
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

    /**
     * 封装 upsertWithHybridFields 方法，自动填充时间戳
     *
     * @param chunkId chunk ID
     * @param embedding 向量字符串
     * @param docType 文档类型
     * @param routeAction 路由动作
     * @param text 原文
     * @param sectionPath 章节路径
     * @param sceneType 场景类型
     * @param sceneTitle 场景标题
     * @param symptomText 症状描述
     * @param questions 问题列表
     * @param answer 答案内容
     */
    default void upsertWithHybridFields(Long chunkId, String embedding, String docType, String routeAction,
                                        String text, String sectionPath, String sceneType, String sceneTitle,
                                        String symptomText, String questions, String answer) {
        LocalDateTime now = LocalDateTime.now();
        upsertWithHybridFields(chunkId, embedding, docType, routeAction,
                text, sectionPath, sceneType, sceneTitle, symptomText, questions, answer,
                now, now);
    }
}
