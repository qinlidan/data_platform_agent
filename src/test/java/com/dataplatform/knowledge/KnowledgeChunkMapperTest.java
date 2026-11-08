package com.dataplatform.knowledge;

import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2 - KnowledgeChunkMapper CRUD 测试
 * TDD: 先写测试，确认 RED，再实现功能
 */
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
        assertNotNull(chunk.getId(), "ID should be generated after insert");

        KnowledgeChunk fetched = chunkMapper.selectById(chunk.getId());
        assertNotNull(fetched, "Should be able to fetch inserted chunk");
        assertEquals("spark调优", fetched.getDocType());
        assertEquals("pending_vectorization", fetched.getVectorizeStatus());
        assertEquals("Spark参数调优", fetched.getCategory());
        assertEquals("slow", fetched.getSceneType());
        assertEquals("作业慢", fetched.getSceneTitle());
        assertTrue(fetched.getIsTunable());
    }

    @Test
    void testUpdateVectorizeStatus() {
        // Insert 2 chunks
        KnowledgeChunk chunk1 = createTestChunk("chunk1");
        KnowledgeChunk chunk2 = createTestChunk("chunk2");
        chunkMapper.insert(chunk1);
        chunkMapper.insert(chunk2);

        assertNotNull(chunk1.getId());
        assertNotNull(chunk2.getId());

        // Update status
        chunkMapper.updateVectorizeStatus(
                Arrays.asList(chunk1.getId(), chunk2.getId()),
                "vectorized"
        );

        // Verify
        assertEquals("vectorized", chunkMapper.selectById(chunk1.getId()).getVectorizeStatus());
        assertEquals("vectorized", chunkMapper.selectById(chunk2.getId()).getVectorizeStatus());
    }

    @Test
    void testInsertBatch() {
        KnowledgeChunk chunk1 = createTestChunk("batch1");
        KnowledgeChunk chunk2 = createTestChunk("batch2");
        KnowledgeChunk chunk3 = createTestChunk("batch3");

        List<KnowledgeChunk> chunks = Arrays.asList(chunk1, chunk2, chunk3);
        chunkMapper.insertBatch(chunks);

        // Verify all have IDs
        chunks.forEach(chunk -> assertNotNull(chunk.getId(), "Each chunk should have an ID"));

        // Verify can be fetched
        KnowledgeChunk fetched1 = chunkMapper.selectById(chunk1.getId());
        assertNotNull(fetched1);
        assertEquals("batch1", fetched1.getQuestions());
    }

    @Test
    void testSelectBatchIds() {
        KnowledgeChunk chunk1 = createTestChunk("select1");
        KnowledgeChunk chunk2 = createTestChunk("select2");
        chunkMapper.insert(chunk1);
        chunkMapper.insert(chunk2);

        List<Long> ids = Arrays.asList(chunk1.getId(), chunk2.getId());
        List<KnowledgeChunk> fetched = chunkMapper.selectBatchIds(ids);

        assertEquals(2, fetched.size());
        assertTrue(fetched.stream().anyMatch(c -> "select1".equals(c.getQuestions())));
        assertTrue(fetched.stream().anyMatch(c -> "select2".equals(c.getQuestions())));
    }

    private KnowledgeChunk createTestChunk(String questionsId) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("spark调优");
        chunk.setDocId("test-doc");
        chunk.setCategory("测试类别");
        chunk.setSceneType("slow");
        chunk.setSceneTitle("测试场景");
        chunk.setQuestions(questionsId);
        chunk.setAnswer("测试答案");
        chunk.setIsTunable(false);
        return chunk;
    }
}
