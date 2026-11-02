package com.dataplatform.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.service.VectorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VectorizationRecoveryTaskTest {
    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private VectorizationRecoveryTask recoveryTask;

    @Test
    @Transactional
    void testFindPendingChunks() {
        // 手动插入pending状态的chunk
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocType("spark调优");
        chunk.setDocId("spark调优");
        chunk.setCategory("测试");
        chunk.setSceneType("slow");
        chunk.setSceneTitle("测试场景");
        chunk.setQuestions("测试问题");
        chunk.setAnswer("测试答案");
        chunk.setVectorizeStatus("pending_vectorization");
        chunk.setCreatedAt(java.time.LocalDateTime.now());
        chunk.setUpdatedAt(java.time.LocalDateTime.now());
        chunkMapper.insert(chunk);

        // 验证能查询到pending状态的chunk
        List<KnowledgeChunk> pending = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getVectorizeStatus, "pending_vectorization")
        );

        assertTrue(pending.size() >= 1, "Should find at least 1 pending chunk");
        assertTrue(pending.stream().anyMatch(c -> c.getId().equals(chunk.getId())),
                   "Should contain the inserted chunk");
    }
}
