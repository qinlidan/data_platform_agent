package com.dataplatform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class KnowledgeImportServiceTest {
    @Autowired
    private KnowledgeImportService importService;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeVectorRepository vectorRepository;

    @Test
    void testImportDocument() throws IOException {
        // 创建测试Markdown文件
        File tempFile = File.createTempFile("test-doc", ".md");
        Files.writeString(tempFile.toPath(), """
            # Spark参数调优

            ## 作业慢

            ### 小文件过多

            调整 spark.sql.files.maxRecordsPerFile 参数...
            """);

        // 导入文档
        importService.importDocument(tempFile, "test-doc", "test-doc");

        // 验证MySQL已插入
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, "test-doc")
        );
        assertFalse(chunks.isEmpty());

        // 清理
        tempFile.delete();
    }

    @Test
    void testReimportOverwrite() throws IOException, InterruptedException {
        // 第一次导入
        File tempFile = File.createTempFile("test-doc", ".md");
        Files.writeString(tempFile.toPath(), "# 旧内容\n\n## 场景1\n\n### 问题1\n\n答案1");
        importService.importDocument(tempFile, "test-doc", "test-doc");
        Thread.sleep(3000);

        Long oldChunkId = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, "test-doc")
        ).get(0).getId();

        // 第二次导入（全量覆盖）
        Files.writeString(tempFile.toPath(), "# 新内容\n\n## 场景2\n\n### 问题2\n\n答案2");
        importService.importDocument(tempFile, "test-doc", "test-doc");

        // 验证旧数据已删除
        assertNull(chunkMapper.selectById(oldChunkId));
        assertFalse(vectorRepository.existsById(oldChunkId));

        // 验证新数据已插入
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, "test-doc")
        );
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getAnswer().contains("答案2"));

        tempFile.delete();
    }
}
