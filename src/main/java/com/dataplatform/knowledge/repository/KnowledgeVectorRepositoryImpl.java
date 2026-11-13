package com.dataplatform.knowledge.repository;

import com.dataplatform.knowledge.dto.VectorSearchResult;
import com.dataplatform.knowledge.dto.VectorSearchResultDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义 Repository 实现
 * 命名规则：接口名 + Impl（Spring Data JPA 自动识别）
 *
 * 通过 EntityManager 执行原生 SQL，绕过 JSqlParser 对 pgvector <=> 运算符的解析限制
 */
@Repository
public class KnowledgeVectorRepositoryImpl implements KnowledgeVectorRepositoryCustom {

    @PersistenceContext(unitName = "postgresEntityManagerFactory")
    private EntityManager entityManager;

    @Override
    public List<VectorSearchResult> searchSimilar(String embedding, String docType, int limit) {
        // 动态构建 SQL，避免 PostgreSQL 参数类型推断问题
        String baseSql = """
            SELECT chunk_id,
                   doc_type,
                   route_action,
                   1 - (embedding <=> CAST(?1 AS vector)) AS score
            FROM knowledge_vectors
            """;

        String sql;
        int embeddingParamIndex;
        int limitParamIndex;

        if (docType != null && !docType.trim().isEmpty()) {
            // 有 docType 过滤
            sql = baseSql + "WHERE doc_type = ?2\n" +
                  "ORDER BY embedding <=> CAST(?3 AS vector)\n" +
                  "LIMIT ?4";
            embeddingParamIndex = 3;
            limitParamIndex = 4;
        } else {
            // 无 docType 过滤
            sql = baseSql +
                  "ORDER BY embedding <=> CAST(?2 AS vector)\n" +
                  "LIMIT ?3";
            embeddingParamIndex = 2;
            limitParamIndex = 3;
        }

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, embedding);

        if (docType != null && !docType.trim().isEmpty()) {
            query.setParameter(2, docType);
        }
        query.setParameter(embeddingParamIndex, embedding);
        query.setParameter(limitParamIndex, limit);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<VectorSearchResult> dtoList = new ArrayList<>();
        for (Object[] row : results) {
            VectorSearchResultDto dto = new VectorSearchResultDto();
            dto.setChunkId(((Number) row[0]).longValue());
            dto.setDocType((String) row[1]);
            dto.setRouteAction((String) row[2]);

            // score 可能是 Double 或 BigDecimal，统一转换
            Object scoreObj = row[3];
            if (scoreObj instanceof Double) {
                dto.setScore(BigDecimal.valueOf((Double) scoreObj));
            } else if (scoreObj instanceof BigDecimal) {
                dto.setScore((BigDecimal) scoreObj);
            } else if (scoreObj instanceof Number) {
                dto.setScore(BigDecimal.valueOf(((Number) scoreObj).doubleValue()));
            }

            dtoList.add(new VectorSearchResultAdapter(dto));
        }

        return dtoList;
    }

    /**
     * 适配器：将 DTO 适配为接口
     */
    private static class VectorSearchResultAdapter implements VectorSearchResult {
        private final VectorSearchResultDto dto;

        public VectorSearchResultAdapter(VectorSearchResultDto dto) {
            this.dto = dto;
        }

        @Override
        public Long getChunkId() {
            return dto.getChunkId();
        }

        @Override
        public String getDocType() {
            return dto.getDocType();
        }

        @Override
        public String getRouteAction() {
            return dto.getRouteAction();
        }

        @Override
        public Double getScore() {
            return dto.getScoreAsDouble();
        }
    }
}
