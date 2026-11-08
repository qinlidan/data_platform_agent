package com.dataplatform.knowledge;

import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2 - KnowledgeChunk实体类和Mapper简化集成测试
 *
 * 测试策略：
 * 1. 使用真实数据库连接
 * 2. 使用@Transactional自动回滚测试数据
 * 3. 验证MyBatis-Plus基本CRUD功能
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class KnowledgeChunkMapperIntegrationTest {

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    /**
     * 测试基本插入和查询功能
     */
    @Test
    void testBasicInsertAndSelect() {
        // Given: 创建一个测试chunk
        KnowledgeChunk chunk = createTestChunk("测试问题");

        // When: 插入数据库
        int result = chunkMapper.insert(chunk);

        // Then: 验证插入成功并能查询
        assertEquals(1, result, "Insert should return 1");
        assertNotNull(chunk.getId(), "ID should be auto-generated");

        KnowledgeChunk fetched = chunkMapper.selectById(chunk.getId());
        assertNotNull(fetched, "Should be able to fetch inserted chunk");
        assertEquals("spark调优", fetched.getDocType());
        assertEquals("pending_vectorization", fetched.getVectorizeStatus());
    }

    /**
     * 测试批量状态更新
     */
    @Test
    void testBatchUpdateStatus() {
        // Given: 插入2个chunks
        KnowledgeChunk chunk1 = createTestChunk("问题1");
        KnowledgeChunk chunk2 = createTestChunk("问题2");
        chunkMapper.insert(chunk1);
        chunkMapper.insert(chunk2);

        List<Long> ids = Arrays.asList(chunk1.getId(), chunk2.getId());

        // When: 批量更新状态
        chunkMapper.updateVectorizeStatus(ids, "vectorized");

        // Then: 验证状态已更新
        assertEquals("vectorized", chunkMapper.selectById(chunk1.getId()).getVectorizeStatus());
        assertEquals("vectorized", chunkMapper.selectById(chunk2.getId()).getVectorizeStatus());
    }

    /**
     * 测试批量插入
     */
    @Test
    void testBatchInsert() {
        // Given: 创建3个chunks
        List<KnowledgeChunk> chunks = Arrays.asList(
            createTestChunk("批量1"),
            createTestChunk("批量2"),
            createTestChunk("批量3")
        );

        // When: 批量插入
        chunkMapper.insertBatch(chunks);

        // Then: 验证所有chunk都有ID
        chunks.forEach(chunk -> {
            assertNotNull(chunk.getId(), "Each chunk should have an ID");
            KnowledgeChunk fetched = chunkMapper.selectById(chunk.getId());
            assertNotNull(fetched, "Each chunk should be fetchable");
        });
    }

    /**
     * 测试selectBatchIds
     */
    @Test
    void testSelectBatchIds() {
        // Given: 插入2个chunks
        KnowledgeChunk chunk1 = createTestChunk("选择1");
        KnowledgeChunk chunk2 = createTestChunk("选择2");
        chunkMapper.insert(chunk1);
        chunkMapper.insert(chunk2);

        // When: 批量查询
        List<Long> ids = Arrays.asList(chunk1.getId(), chunk2.getId());
        List<KnowledgeChunk> fetched = chunkMapper.selectBatchIds(ids);

        // Then: 验证查询结果
        assertEquals(2, fetched.size());
        assertTrue(fetched.stream().anyMatch(c -> "选择1".equals(c.getQuestions())));
        assertTrue(fetched.stream().anyMatch(c -> "选择2".equals(c.getQuestions())));
    }

    /**
     * 创建测试用的KnowledgeChunk
     */
    private KnowledgeChunk createTestChunk(String question) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("spark调优");
        chunk.setDocId("test-doc");
        chunk.setCategory("测试类别");
        chunk.setSceneType("slow");
        chunk.setSceneTitle("测试场景");
        chunk.setQuestions(question);
        chunk.setAnswer("测试答案");
        chunk.setIsTunable(false);
        return chunk;
    }
}
