package com.dataplatform.knowledge;

import com.dataplatform.dto.ChunkDTO;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2 单元测试 - 验证实体类和Mapper接口
 *
 * 测试策略：
 * 1. 验证KnowledgeChunk实体类所有字段
 * 2. 验证ChunkDTO所有字段
 * 3. 验证KnowledgeChunkMapper接口定义
 * 4. 不依赖数据库连接，纯单元测试
 *
 * @author Task 2 - TDD Verification
 */
class Task2EntityMapperUnitTest {

    @Test
    void testKnowledgeChunkEntityFields() {
        // Given: 创建一个KnowledgeChunk实例
        KnowledgeChunk chunk = new KnowledgeChunk();

        // When: 设置所有字段
        chunk.setId(1L);
        chunk.setDocType("spark调优");
        chunk.setDocId("doc001");
        chunk.setCategory("执行慢");
        chunk.setSceneType("slow");
        chunk.setSceneTitle("Shuffle阶段耗时长");
        chunk.setSymptomText("任务在shuffle阶段停滞");
        chunk.setQuestions("如何优化shuffle性能？");
        chunk.setAnswer("增加executor内存...");
        chunk.setSectionPath("1.2.3");
        chunk.setContentType("faq");
        chunk.setIsTunable(true);
        chunk.setRouteAction("tunable");
        chunk.setPrevChunkId(0L);
        chunk.setNextChunkId(2L);
        chunk.setVectorizeStatus("pending_vectorization");
        chunk.setCreatedAt(LocalDateTime.now());
        chunk.setUpdatedAt(LocalDateTime.now());

        // Then: 验证所有getter方法
        assertEquals(1L, chunk.getId());
        assertEquals("spark调优", chunk.getDocType());
        assertEquals("doc001", chunk.getDocId());
        assertEquals("执行慢", chunk.getCategory());
        assertEquals("slow", chunk.getSceneType());
        assertEquals("Shuffle阶段耗时长", chunk.getSceneTitle());
        assertEquals("任务在shuffle阶段停滞", chunk.getSymptomText());
        assertEquals("如何优化shuffle性能？", chunk.getQuestions());
        assertEquals("增加executor内存...", chunk.getAnswer());
        assertEquals("1.2.3", chunk.getSectionPath());
        assertEquals("faq", chunk.getContentType());
        assertTrue(chunk.getIsTunable());
        assertEquals("tunable", chunk.getRouteAction());
        assertEquals(0L, chunk.getPrevChunkId());
        assertEquals(2L, chunk.getNextChunkId());
        assertEquals("pending_vectorization", chunk.getVectorizeStatus());
        assertNotNull(chunk.getCreatedAt());
        assertNotNull(chunk.getUpdatedAt());
    }

    @Test
    void testChunkDTOFields() {
        // Given: 创建一个ChunkDTO实例
        ChunkDTO dto = new ChunkDTO();

        // When: 设置所有字段
        dto.setDocType("spark调优");
        dto.setDocId("doc001");
        dto.setCategory("执行慢");
        dto.setSceneType("slow");
        dto.setSceneTitle("Shuffle阶段耗时长");
        dto.setSymptomText("任务在shuffle阶段停滞");
        dto.setQuestions("如何优化shuffle性能？");
        dto.setAnswer("增加executor内存...");
        dto.setSectionPath("1.2.3");
        dto.setContentType("faq");
        dto.setIsTunable(true);
        dto.setRouteAction("tunable");

        // Then: 验证所有getter方法
        assertEquals("spark调优", dto.getDocType());
        assertEquals("doc001", dto.getDocId());
        assertEquals("执行慢", dto.getCategory());
        assertEquals("slow", dto.getSceneType());
        assertEquals("Shuffle阶段耗时长", dto.getSceneTitle());
        assertEquals("任务在shuffle阶段停滞", dto.getSymptomText());
        assertEquals("如何优化shuffle性能？", dto.getQuestions());
        assertEquals("增加executor内存...", dto.getAnswer());
        assertEquals("1.2.3", dto.getSectionPath());
        assertEquals("faq", dto.getContentType());
        assertTrue(dto.getIsTunable());
        assertEquals("tunable", dto.getRouteAction());
    }

    @Test
    void testKnowledgeChunkMapperInterfaceExists() {
        // Then: 验证Mapper接口存在且继承BaseMapper
        assertTrue(com.baomidou.mybatisplus.core.mapper.BaseMapper.class
            .isAssignableFrom(KnowledgeChunkMapper.class));
    }

    @Test
    void testKnowledgeChunkMapperHasCustomMethods() throws NoSuchMethodException {
        // Then: 验证自定义方法存在
        Method insertBatch = KnowledgeChunkMapper.class
            .getDeclaredMethod("insertBatch", java.util.List.class);
        assertNotNull(insertBatch);

        Method updateStatus = KnowledgeChunkMapper.class
            .getDeclaredMethod("updateVectorizeStatus", java.util.List.class, String.class);
        assertNotNull(updateStatus);
    }

    @Test
    void testKnowledgeChunkDefaultValues() {
        // Given & When: 创建空实体
        KnowledgeChunk chunk = new KnowledgeChunk();

        // Then: 验证默认值
        assertNull(chunk.getId());
        assertNull(chunk.getDocType());
        assertNull(chunk.getVectorizeStatus());
    }

    @Test
    void testChunkDTODefaultValues() {
        // Given & When: 创建空DTO
        ChunkDTO dto = new ChunkDTO();

        // Then: 验证默认值
        assertNull(dto.getDocType());
        assertNull(dto.getIsTunable());
    }
}
