package com.dataplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.chunker.ChunkerPipeline;
import com.dataplatform.dto.ChunkDTO;
import com.dataplatform.dto.knowledge.*;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeManagementService {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeVectorRepository vectorRepository;
    private final ChunkerPipeline chunkerPipeline;
    private final VectorizationService vectorizationService;
    private final VectorSearchService vectorSearchService;

    /**
     * 获取文档列表
     */
    public List<DocumentInfoDTO> listDocuments() {
        // 扫描data/query目录
        Path dataQueryPath = Paths.get("data/query");
        List<DocumentInfoDTO> documents = new ArrayList<>();

        try {
            if (Files.exists(dataQueryPath) && Files.isDirectory(dataQueryPath)) {
                Files.list(dataQueryPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".markdown"))
                    .forEach(filePath -> {
                        try {
                            String fileName = filePath.getFileName().toString();
                            String docId = fileName.replaceAll("\\.(md|markdown)$", "");

                            DocumentInfoDTO dto = new DocumentInfoDTO();
                            dto.setFileName(fileName);
                            dto.setDocId(docId);

                            // 判断docType：根据文件名或从数据库读取
                            List<KnowledgeChunk> chunks = chunkMapper.selectList(
                                Wrappers.<KnowledgeChunk>lambdaQuery()
                                    .eq(KnowledgeChunk::getDocId, docId)
                                    .last("LIMIT 1")
                            );

                            if (!chunks.isEmpty()) {
                                dto.setDocType(chunks.get(0).getDocType());
                                dto.setImportTime(chunks.get(0).getCreatedAt());
                            } else {
                                // 默认类型：根据文件名判断
                                dto.setDocType(docId.toLowerCase().contains("faq") ? "FAQ" : "manual");
                                dto.setImportTime(null);
                            }

                            dto.setFileSize(Files.size(filePath));
                            dto.setLastModified(Files.getLastModifiedTime(filePath).toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());

                            // 查询chunk数量
                            Long chunkCount = chunkMapper.selectCount(
                                Wrappers.<KnowledgeChunk>lambdaQuery()
                                    .eq(KnowledgeChunk::getDocId, docId)
                            );
                            dto.setChunkCount(chunkCount.intValue());

                            documents.add(dto);
                        } catch (IOException e) {
                            log.error("读取文件信息失败: {}", filePath, e);
                        }
                    });
            }
        } catch (IOException e) {
            log.error("扫描data/query目录失败", e);
        }

        return documents;
    }

    /**
     * 重新导入文档
     */
    @Transactional
    public ReimportResultDTO reimportDocument(String docId) {
        ReimportResultDTO result = new ReimportResultDTO();

        try {
            // 1. 查找文件
            Path filePath = Paths.get("data/query", docId + ".md");
            if (!Files.exists(filePath)) {
                filePath = Paths.get("data/query", docId + ".markdown");
            }

            if (!Files.exists(filePath)) {
                result.setSuccess(false);
                result.setMessage("文档文件不存在: " + docId);
                return result;
            }

            // 2. 删除旧数据
            List<KnowledgeChunk> oldChunks = chunkMapper.selectList(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getDocId, docId)
            );

            if (!oldChunks.isEmpty()) {
                List<Long> oldChunkIds = oldChunks.stream()
                    .map(KnowledgeChunk::getId)
                    .collect(Collectors.toList());
                chunkMapper.deleteBatchIds(oldChunkIds);
                oldChunkIds.forEach(vectorRepository::deleteById);
            }

            // 3. 读取并切分
            String markdown = Files.readString(filePath);
            List<ChunkDTO> chunkDTOs = chunkerPipeline.process(markdown, docId, docId);

            // 4. 保存到MySQL
            LocalDateTime now = LocalDateTime.now();
            List<Long> newChunkIds = new ArrayList<>();

            for (ChunkDTO dto : chunkDTOs) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocType(dto.getDocType());
                chunk.setDocId(dto.getDocId());
                chunk.setCategory(dto.getCategory());
                chunk.setSceneType(dto.getSceneType());
                chunk.setSceneTitle(dto.getSceneTitle());
                chunk.setQuestions(dto.getQuestions());
                chunk.setAnswer(dto.getAnswer());
                chunk.setSectionPath(dto.getSectionPath());
                chunk.setVectorizeStatus("pending_vectorization");
                chunk.setCreatedAt(now);
                chunk.setUpdatedAt(now);
                chunkMapper.insert(chunk);
                newChunkIds.add(chunk.getId());
            }

            // 5. 触发向量化
            vectorizationService.vectorizeChunksAsync(newChunkIds);

            result.setSuccess(true);
            result.setMessage("重新导入成功");
            result.setChunkCount(newChunkIds.size());

        } catch (Exception e) {
            log.error("重新导入文档失败: {}", docId, e);
            result.setSuccess(false);
            result.setMessage("重新导入失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 查看文档切块
     */
    public ChunkPageDTO listChunks(String docId, int page, int size) {
        Page<KnowledgeChunk> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeChunk> query = Wrappers.<KnowledgeChunk>lambdaQuery()
            .eq(KnowledgeChunk::getDocId, docId)
            .orderByDesc(KnowledgeChunk::getCreatedAt);

        IPage<KnowledgeChunk> pageResult = chunkMapper.selectPage(pageParam, query);

        ChunkPageDTO result = new ChunkPageDTO();
        result.setTotal(pageResult.getTotal());

        List<ChunkPageDTO.ChunkItemDTO> items = pageResult.getRecords().stream()
            .map(chunk -> {
                ChunkPageDTO.ChunkItemDTO item = new ChunkPageDTO.ChunkItemDTO();
                item.setId(chunk.getId());
                item.setQuestions(chunk.getQuestions());
                item.setAnswer(chunk.getAnswer());
                item.setSceneType(chunk.getSceneType());
                item.setSceneTitle(chunk.getSceneTitle());
                item.setSectionPath(chunk.getSectionPath());
                item.setVectorizeStatus(chunk.getVectorizeStatus());
                return item;
            })
            .collect(Collectors.toList());

        result.setChunks(items);
        return result;
    }

    /**
     * 预览切分结果（含查重）
     */
    public PreviewResultDTO previewChunks(ManualInputRequest request) {
        try {
            // 1. 切分
            List<ChunkDTO> chunkDTOs = chunkerPipeline.process(
                request.getMarkdownText(),
                "manual-input",
                request.getDocType()
            );

            // 2. 查重检测
            List<PreviewResultDTO.PreviewChunkDTO> previewChunks = new ArrayList<>();

            for (int i = 0; i < chunkDTOs.size(); i++) {
                ChunkDTO dto = chunkDTOs.get(i);
                PreviewResultDTO.PreviewChunkDTO previewChunk = new PreviewResultDTO.PreviewChunkDTO();

                previewChunk.setTempId("temp-" + i);
                previewChunk.setQuestions(dto.getQuestions());
                previewChunk.setAnswer(dto.getAnswer());
                previewChunk.setSceneType(dto.getSceneType());
                previewChunk.setSceneTitle(dto.getSceneTitle());
                previewChunk.setSectionPath(dto.getSectionPath());

                // 查重：查询是否有相似questions
                boolean isDuplicate = checkDuplicate(dto.getQuestions());
                previewChunk.setIsDuplicate(isDuplicate);
                previewChunk.setSimilarity(isDuplicate ? 0.95 : null);

                previewChunks.add(previewChunk);
            }

            PreviewResultDTO result = new PreviewResultDTO();
            result.setChunks(previewChunks);
            return result;

        } catch (Exception e) {
            log.error("预览切分失败", e);
            throw new RuntimeException("预览切分失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否重复（简化实现：查询questions是否已存在）
     */
    private boolean checkDuplicate(String questions) {
        if (questions == null || questions.trim().isEmpty()) {
            return false;
        }

        Long count = chunkMapper.selectCount(
            Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getQuestions, questions)
        );

        return count > 0;
    }

    /**
     * 手工录入确认入库
     */
    @Transactional
    public ImportResultDTO importManualChunks(ManualImportRequest request) {
        ImportResultDTO result = new ImportResultDTO();

        try {
            // 过滤选中的chunks
            Set<String> selectedIds = new HashSet<>(request.getSelectedTempIds());
            List<PreviewResultDTO.PreviewChunkDTO> selectedChunks = request.getChunks().stream()
                .filter(chunk -> selectedIds.contains(chunk.getTempId()))
                .collect(Collectors.toList());

            if (selectedChunks.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("未选择任何切块");
                return result;
            }

            // 保存到MySQL
            LocalDateTime now = LocalDateTime.now();
            List<Long> newChunkIds = new ArrayList<>();

            for (PreviewResultDTO.PreviewChunkDTO previewChunk : selectedChunks) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocType(request.getDocType());
                chunk.setDocId("manual-" + System.currentTimeMillis());
                chunk.setCategory("manual");
                chunk.setSceneType(previewChunk.getSceneType());
                chunk.setSceneTitle(previewChunk.getSceneTitle());
                chunk.setQuestions(previewChunk.getQuestions());
                chunk.setAnswer(previewChunk.getAnswer());
                chunk.setSectionPath(previewChunk.getSectionPath());
                chunk.setVectorizeStatus("pending_vectorization");
                chunk.setCreatedAt(now);
                chunk.setUpdatedAt(now);
                chunkMapper.insert(chunk);
                newChunkIds.add(chunk.getId());
            }

            // 触发向量化
            vectorizationService.vectorizeChunksAsync(newChunkIds);

            result.setSuccess(true);
            result.setMessage("导入成功");
            result.setImportedCount(newChunkIds.size());

        } catch (Exception e) {
            log.error("手工导入失败", e);
            result.setSuccess(false);
            result.setMessage("导入失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 检索自测
     */
    public List<SearchResultDTO> searchTest(SearchTestRequest request) {
        try {
            // 调用向量检索服务
            List<Map<String, Object>> searchResults = vectorSearchService.searchWithMap(
                request.getQuery(),
                request.getDocType(),
                request.getTopK() != null ? request.getTopK() : 5
            );

            return searchResults.stream()
                .map(map -> {
                    SearchResultDTO dto = new SearchResultDTO();
                    dto.setChunkId(((Number) map.get("id")).longValue());
                    dto.setQuestions((String) map.get("questions"));
                    dto.setAnswer((String) map.get("answer"));
                    dto.setScore(((Number) map.get("score")).doubleValue());
                    dto.setDocType((String) map.get("docType"));
                    dto.setDocId((String) map.get("docId"));
                    dto.setSectionPath((String) map.get("sectionPath"));
                    return dto;
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("检索测试失败", e);
            throw new RuntimeException("检索测试失败: " + e.getMessage());
        }
    }
}
