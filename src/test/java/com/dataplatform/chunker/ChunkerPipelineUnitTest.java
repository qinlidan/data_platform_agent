package com.dataplatform.chunker;

import com.dataplatform.dto.ChunkDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunker Pipeline单元测试（不依赖Spring Context）
 */
class ChunkerPipelineUnitTest {

    private ChunkerPipeline chunkerPipeline;

    @BeforeEach
    void setUp() {
        chunkerPipeline = new ChunkerPipeline();
    }

    @Test
    void testQaPairChunking() {
        String markdown = """
            ## 作业报错

            **Q1：OOM内存溢出如何处理？**
            **A1：**调整 spark.executor.memory 参数...

            **Q2：找不到表如何排查？**
            **A2：**检查库名和权限...
            """;

        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "spark调优", "spark调优");

        assertEquals(2, chunks.size());
        assertEquals("OOM内存溢出如何处理？", chunks.get(0).getQuestions());
        assertTrue(chunks.get(0).getAnswer().contains("spark.executor.memory"));
        assertEquals("error", chunks.get(0).getSceneType());
        assertEquals("作业报错", chunks.get(0).getSceneTitle());
    }

    @Test
    void testTableRowChunking() {
        String markdown = """
            ## 名词解释

            | 名词 | 解释 |
            |-----|------|
            | OOM | 内存溢出 |
            | GC  | 垃圾回收 |
            """;

        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "用户手册", "数据研发平台用户手册");

        assertEquals(2, chunks.size());
        assertEquals("OOM", chunks.get(0).getQuestions());
        assertTrue(chunks.get(0).getAnswer().contains("内存溢出"));
        assertTrue(chunks.get(0).getAnswer().contains("名词："));
    }

    @Test
    void testHeadingHierarchyChunking() {
        String markdown = """
            # Spark参数调优

            ## 作业慢

            ### 1. 小文件过多

            现象：写文件产生大量小文件，NameNode压力大。

            解决方案：调整 spark.sql.files.maxRecordsPerFile 参数...
            """;

        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "spark调优", "spark调优");

        assertFalse(chunks.isEmpty());
        ChunkDTO chunk = chunks.get(0);
        assertEquals("Spark参数调优", chunk.getCategory());
        assertEquals("作业慢", chunk.getSceneTitle());
        assertEquals("slow", chunk.getSceneType());
        assertEquals("1. 小文件过多", chunk.getQuestions());
        assertTrue(chunk.getSymptomText().contains("写文件产生大量小文件"));
        assertTrue(chunk.getAnswer().contains("spark.sql.files.maxRecordsPerFile"));
    }

    @Test
    void testRecursiveFallbackChunking() {
        // 测试普通短文本（不超过1000字）
        String markdown = "这是一段普通的文本，不包含特殊的Markdown结构。";

        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "测试文档", "测试文档");

        assertEquals(1, chunks.size());
        assertEquals("测试文档", chunks.get(0).getQuestions());
        assertEquals(markdown, chunks.get(0).getAnswer());
    }

    @Test
    void testSceneTypeMapping() {
        // 测试各种sceneType的推断
        String[] sceneTitles = {
            "作业报错",      // error
            "作业慢",        // slow
            "任务pending",   // pending
            "资源超限",      // boundary
            "操作指南",      // guide
            "概念说明",      // concept
            "FAQ问答"        // faq
        };

        String[] expectedTypes = {
            "error", "slow", "pending", "boundary", "guide", "concept", "faq"
        };

        for (int i = 0; i < sceneTitles.length; i++) {
            String markdown = String.format("## %s\n\n### 问题1\n\n答案1", sceneTitles[i]);
            List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "spark调优", "spark调优");

            assertFalse(chunks.isEmpty());
            assertEquals(expectedTypes[i], chunks.get(0).getSceneType(),
                "SceneTitle '" + sceneTitles[i] + "' 应该映射到 " + expectedTypes[i]);
        }
    }

    @Test
    void testSectionPathGeneration() {
        String markdown = """
            # Spark参数调优

            ## 作业慢

            ### 1. 小文件过多

            解决方案：调整参数...
            """;

        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "spark调优", "spark调优");

        assertEquals(1, chunks.size());
        assertEquals("Spark参数调优 / 作业慢 / 1. 小文件过多", chunks.get(0).getSectionPath());
    }

    @Test
    void testEmptyMarkdown() {
        String markdown = "";

        List<ChunkDTO> chunks = chunkerPipeline.process(markdown, "空文档", "空文档");

        // 兜底切分器会处理空文档
        assertNotNull(chunks);
    }
}
