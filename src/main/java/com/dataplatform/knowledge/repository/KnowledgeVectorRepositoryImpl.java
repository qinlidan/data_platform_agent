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
        String sql = """
            SELECT chunk_id,
                   doc_type,
                   route_action,
                   1 - (embedding <=> CAST(?1 AS vector)) AS score
            FROM knowledge_vectors
            WHERE (?2 IS NULL OR doc_type = ?3)
            ORDER BY embedding <=> CAST(?4 AS vector)
            LIMIT ?5
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, embedding);
        query.setParameter(2, docType);
        query.setParameter(3, docType);
        query.setParameter(4, embedding);
        query.setParameter(5, limit);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<VectorSearchResult> dtoList = new ArrayList<>();
        for (Object[] row : results) {
            VectorSearchResultDto dto = new VectorSearchResultDto();
            dto.setChunkId(((Number) row[0]).longValue());
            dto.setDocType((String) row[1]);
            dto.setRouteAction((String) row[2]);
            dto.setScore((BigDecimal) row[3]);
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
