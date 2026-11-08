package com.dataplatform.chunker;

import com.dataplatform.dto.ChunkDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunker Pipeline测试类
 * 测试QA对、表格行、标题层级、递归兜底切分器
 */
@SpringBootTest
class ChunkerPipelineTest {

    @Autowired
    private ChunkerPipeline chunkerPipeline;

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
        assertEquals("Spark参数调优", chunks.get(0).getCategory());
        assertEquals("作业慢", chunks.get(0).getSceneTitle());
        assertEquals("slow", chunks.get(0).getSceneType());
        assertEquals("1. 小文件过多", chunks.get(0).getQuestions());
    }
}
