package com.dataplatform.knowledge.repository;

import com.dataplatform.knowledge.dto.VectorSearchResult;
import com.dataplatform.knowledge.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * pgvector Repository测试
 * Task 4 - Step 1
 */
@SpringBootTest
@ActiveProfiles("test")
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
        assertTrue(results.get(0).getScore() > 0.5, "Similarity score should be > 0.5");
    }

    @Test
    void testUpsertIdempotent() {
        float[] embedding = new float[1024];
        Arrays.fill(embedding, 0.1f);
        String embeddingStr = arrayToString(embedding);

        vectorRepository.upsert(2L, embeddingStr, "spark调优", "tunable");
        vectorRepository.flush();
        long countAfterFirst = vectorRepository.count();

        vectorRepository.upsert(2L, embeddingStr, "spark调优", "tunable");  // 重复upsert
        vectorRepository.flush();
        long countAfterSecond = vectorRepository.count();

        assertEquals(countAfterFirst, countAfterSecond, "应该只有1条记录");
    }

    @Test
    void testCosineDistance() {
        // 测试使用 <=> COSINE操作符
        float[] embedding1 = new float[1024];
        Arrays.fill(embedding1, 0.5f);

        vectorRepository.upsert(10L, arrayToString(embedding1), "test", null);
        vectorRepository.flush();

        // 查询相同的向量，相似度应该接近1.0
        List<VectorSearchResult> results = vectorRepository.searchSimilar(
            arrayToString(embedding1), null, 1
        );

        assertFalse(results.isEmpty());
        assertEquals(10L, results.get(0).getChunkId());
        assertTrue(results.get(0).getScore() > 0.99, "相同向量的COSINE相似度应该接近1.0");
    }

    @Test
    void testDeleteByChunkIdIn() {
        float[] embedding = new float[1024];
        Arrays.fill(embedding, 0.1f);
        String embeddingStr = arrayToString(embedding);

        vectorRepository.upsert(100L, embeddingStr, "spark调优", "tunable");
        vectorRepository.upsert(101L, embeddingStr, "spark调优", "tunable");
        vectorRepository.flush();

        vectorRepository.deleteByChunkIdIn(Arrays.asList(100L, 101L));
        vectorRepository.flush();

        assertFalse(vectorRepository.existsById(100L));
        assertFalse(vectorRepository.existsById(101L));
    }

    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
