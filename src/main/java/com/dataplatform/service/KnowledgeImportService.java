package com.dataplatform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dataplatform.chunker.ChunkerPipeline;
import com.dataplatform.dto.ChunkDTO;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
public class KnowledgeImportService {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeVectorRepository vectorRepository;
    private final ChunkerPipeline chunkerPipeline;
    private final VectorizationService vectorizationService;

    public KnowledgeImportService(KnowledgeChunkMapper chunkMapper,
                                   KnowledgeVectorRepository vectorRepository,
                                   ChunkerPipeline chunkerPipeline,
                                   VectorizationService vectorizationService) {
        this.chunkMapper = chunkMapper;
        this.vectorRepository = vectorRepository;
        this.chunkerPipeline = chunkerPipeline;
        this.vectorizationService = vectorizationService;
    }

    /**
     * 扫描 data/query/ 目录，按doc_id全量覆盖导入
     */
    public void autoImportFromDataQuery() {
        Path dataQueryPath = Paths.get("data/query");

        if (!Files.exists(dataQueryPath) || !Files.isDirectory(dataQueryPath)) {
            log.warn("data/query 目录不存在，跳过自动导入");
            return;
        }

        try (Stream<Path> paths = Files.walk(dataQueryPath, 1)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".markdown"))
                .forEach(filePath -> {
                    String fileName = filePath.getFileName().toString();
                    String docId = fileName.replaceAll("\\.m(ark)?d(own)?$", "");
                    String docType = docId; // 文件名即docType

                    try {
                        importDocument(filePath.toFile(), docId, docType);
                        log.info("自动导入成功: {}", fileName);
                    } catch (Exception e) {
                        log.error("自动导入失败: {} - {}", fileName, e.getMessage(), e);
                    }
                });
        } catch (IOException e) {
            log.error("扫描 data/query 目录失败", e);
        }
    }

    /**
     * 导入单个文档，删除旧数据+切分+双写
     * @param file 文档文件
     * @param docId 文档ID
     * @param docType 文档类型
     */
    @Transactional
    public void importDocument(File file, String docId, String docType) throws IOException {
        log.info("开始导入文档: docId={}, docType={}", docId, docType);

        // 1. 查询旧chunk_ids
        List<KnowledgeChunk> oldChunks = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocId, docId)
        );

        // 2. 删除MySQL和pgvector旧数据
        if (!oldChunks.isEmpty()) {
            List<Long> oldChunkIds = oldChunks.stream().map(KnowledgeChunk::getId).toList();
            log.info("删除旧数据: {} chunks", oldChunkIds.size());
            chunkMapper.deleteBatchIds(oldChunkIds);
            oldChunkIds.forEach(vectorRepository::deleteById);
        }

        // 3. 读取文件
        String markdown = Files.readString(file.toPath());

        // 4. 调用ChunkerPipeline切分
        List<ChunkDTO> chunkDTOs = chunkerPipeline.process(markdown, docId, docType);
        log.info("切分完成: {} chunks", chunkDTOs.size());

        // 5. 保存到MySQL
        LocalDateTime now = LocalDateTime.now();
        List<Long> newChunkIds = chunkDTOs.stream()
            .map(dto -> {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocType(dto.getDocType());
                chunk.setDocId(dto.getDocId());
                chunk.setCategory(dto.getCategory());
                chunk.setSceneType(dto.getSceneType());
                chunk.setSceneTitle(dto.getSceneTitle());
                chunk.setQuestions(dto.getQuestions());
                chunk.setAnswer(dto.getAnswer());
                chunk.setVectorizeStatus("pending_vectorization");
                chunk.setCreatedAt(now);
                chunk.setUpdatedAt(now);
                chunkMapper.insert(chunk);
                return chunk.getId();
            })
            .toList();

        // 6. 触发向量化（异步）
        vectorizationService.vectorizeChunksAsync(newChunkIds);

        log.info("文档导入完成: docId={}, {} chunks submitted for vectorization", docId, newChunkIds.size());
    }
}
