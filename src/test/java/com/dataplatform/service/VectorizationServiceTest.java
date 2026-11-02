package com.dataplatform.service;

import com.dataplatform.dto.ChunkDTO;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorizationService 测试
 * Task 6 - 同步/异步双写服务
 *
 * 测试策略：
 * 1. testSaveChunksSync - 验证同步MySQL写入+pending状态
 * 2. testVectorizeChunksAsync - 验证异步向量化+pgvector写入+状态更新
 */
@SpringBootTest
class VectorizationServiceTest {

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeVectorRepository vectorRepository;

    /**
     * 测试processChunks：MySQL批量插入+触发异步向量化
     * 注：由于异步执行速度快，验证时状态可能已是vectorized
     */
    @Test
    @Transactional
    @Commit
    void testSaveChunksSync() {
        List<ChunkDTO> chunks = Arrays.asList(createTestChunk());

        List<Long> chunkIds = vectorizationService.processChunks(chunks);

        assertFalse(chunkIds.isEmpty());

        // 验证MySQL已插入
        KnowledgeChunk chunk = chunkMapper.selectById(chunkIds.get(0));
        assertNotNull(chunk);
        // 注：状态可能是pending_vectorization或vectorized（异步任务可能已完成）
        assertTrue(chunk.getVectorizeStatus().equals("pending_vectorization")
                || chunk.getVectorizeStatus().equals("vectorized"));
        assertEquals("spark调优", chunk.getDocType());
        assertEquals("小文件过多", chunk.getQuestions());
    }

    /**
     * 测试异步阶段：向量化+pgvector写入+状态回填
     */
    @Test
    @Transactional
    @Commit
    void testVectorizeChunksAsync() throws InterruptedException {
        // 先插入一个chunk
        ChunkDTO dto = createTestChunk();
        List<Long> chunkIds = vectorizationService.processChunks(Arrays.asList(dto));

        // 等待异步任务完成（最多5秒）
        Thread.sleep(5000);

        // 验证pgvector已插入
        assertTrue(vectorRepository.existsById(chunkIds.get(0)));

        // 验证MySQL状态已更新
        KnowledgeChunk chunk = chunkMapper.selectById(chunkIds.get(0));
        assertEquals("vectorized", chunk.getVectorizeStatus());
    }

    /**
     * 创建测试用的ChunkDTO
     */
    private ChunkDTO createTestChunk() {
        ChunkDTO dto = new ChunkDTO();
        dto.setDocType("spark调优");
        dto.setDocId("spark调优");
        dto.setCategory("Spark参数调优");
        dto.setSceneType("slow");
        dto.setSceneTitle("作业慢");
        dto.setQuestions("小文件过多");
        dto.setAnswer("调整写入参数减少小文件数量。可以通过调整 spark.sql.files.maxRecordsPerFile 参数...");
        dto.setSectionPath("Spark参数调优 / 作业慢 / 小文件过多");
        dto.setIsTunable(true);
        dto.setRouteAction("tunable");
        return dto;
    }
}
