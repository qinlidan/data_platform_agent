package com.dataplatform.service;

import com.dataplatform.dto.ChunkDTO;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import com.dataplatform.knowledge.service.EmbeddingService;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量化服务 - 同步/异步双写
 * Task 6 - 同步/异步双写服务
 *
 * 核心功能：
 * 1. 同步阶段：批量写入MySQL，状态为pending_vectorization，立即返回chunk_ids
 * 2. 异步阶段：向量化 + pgvector upsert + MySQL状态回填（completed/failed）
 *
 * @author Task 6
 * @date 2026-11-01
 */
@Service
@Slf4j
public class VectorizationService {

    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final KnowledgeVectorRepository vectorRepository;

    @Autowired
    public VectorizationService(
            KnowledgeChunkMapper chunkMapper,
            EmbeddingService embeddingService,
            KnowledgeVectorRepository vectorRepository) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    /**
     * 处理chunks：同步写MySQL + 异步向量化写PostgreSQL
     *
     * 同步阶段（当前线程）：
     * 1. 将ChunkDTO转换为KnowledgeChunk实体
     * 2. 批量插入MySQL，状态为'pending_vectorization'
     * 3. 获取自增主键chunk_id列表
     * 4. 提交异步向量化任务
     * 5. 立即返回chunk_ids给调用方
     *
     * @param chunks chunk列表
     * @return 同步写入的chunk_id列表
     */
    @Transactional(transactionManager = "mysqlTransactionManager")
    public List<Long> processChunks(List<ChunkDTO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("processChunks called with empty chunks list");
            return new ArrayList<>();
        }

        // 1. 转换为实体并批量插入MySQL
        List<KnowledgeChunk> entities = chunks.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());

        chunkMapper.insertBatch(entities);

        // 2. 获取自增主键列表
        List<Long> chunkIds = entities.stream()
                .map(KnowledgeChunk::getId)
                .collect(Collectors.toList());

        log.info("MySQL 同步写入完成: {} chunks, ids: {}", chunkIds.size(), chunkIds);

        // 3. 提交异步向量化任务
        vectorizeChunksAsync(chunkIds);

        return chunkIds;
    }

    /**
     * 异步向量化任务
     *
     * 异步阶段（线程池执行）：
     * 1. 从MySQL查询chunk原文
     * 2. 按spec 3.2拼接embeddingText
     * 3. 调用EmbeddingService生成1024维向量
     * 4. upsert到pgvector
     * 5. 批量回填MySQL状态为'completed'
     * 6. 异常时记录日志，状态保持'pending_vectorization'（由断点重跑补偿）
     *
     * @param chunkIds 需要向量化的chunk_id列表
     */
    @Async("vectorizationExecutor")
    public void vectorizeChunksAsync(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            log.warn("vectorizeChunksAsync called with empty chunkIds list");
            return;
        }

        log.info("开始异步向量化: {} chunks", chunkIds.size());
        vectorizeChunks(chunkIds);
    }

    /**
     * 向量化逻辑的内部实现（供异步调用）
     */
    private void vectorizeChunks(List<Long> chunkIds) {

        List<Long> successIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        try {
            // 1. 查询MySQL获取原文
            List<KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds);

            // 2. 逐个向量化
            for (KnowledgeChunk chunk : chunks) {
                try {
                    // 2.1 拼接embeddingText（按spec 3.2）
                    String embeddingText = buildEmbeddingText(chunk);

                    // 2.2 调用Embedding服务生成向量
                    float[] embedding = embeddingService.embedDocument(embeddingText);

                    // 2.3 验证向量维度（防止Review Focus #1）
                    if (embedding.length != 1024) {
                        throw new IllegalStateException(
                            String.format("向量维度不匹配: expected 1024 but got %d for chunk_id=%d",
                                    embedding.length, chunk.getId())
                        );
                    }

                    // 2.4 转换为PostgreSQL向量字符串格式
                    String embeddingStr = formatEmbedding(embedding);

                    // 2.5 upsert到pgvector：embedding + 检索/精排/引用所需的冗余文本一起写
                    LocalDateTime now = LocalDateTime.now();
                    vectorRepository.upsert(chunk.getId(), embeddingStr,
                            chunk.getDocType(), chunk.getRouteAction(),
                            buildSearchText(chunk), buildRerankText(chunk), chunk.getSectionPath(),
                            now, now);

                    successIds.add(chunk.getId());
                    log.debug("向量化成功: chunk_id={}", chunk.getId());

                } catch (Exception e) {
                    failedIds.add(chunk.getId());
                    log.error("向量化失败: chunk_id={}, error={}", chunk.getId(), e.getMessage(), e);
                }
            }

            // 3. 批量回填MySQL状态
            if (!successIds.isEmpty()) {
                chunkMapper.updateVectorizeStatus(successIds, "vectorized");
                log.info("异步向量化完成: 成功 {}/{} chunks", successIds.size(), chunkIds.size());
            }

            if (!failedIds.isEmpty()) {
                chunkMapper.updateVectorizeStatus(failedIds, "pending_vectorization");
                log.error("异步向量化失败: 失败 {} chunks: {}", failedIds.size(), failedIds);
            }

        } catch (Exception e) {
            log.error("异步向量化任务失败: chunkIds={}", chunkIds, e);
            // 异常时标记所有chunk为pending_vectorization（支持重试）
            chunkMapper.updateVectorizeStatus(chunkIds, "pending_vectorization");
        }
    }

    /**
     * 重试失败的向量化任务
     *
     * 用于断点重跑和手动补偿
     *
     * @param chunkIds 需要重试的chunk_id列表
     */
    @Async("vectorizationExecutor")
    public void retryVectorization(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            log.warn("retryVectorization called with empty chunkIds list");
            return;
        }

        log.info("开始重试向量化: {} chunks", chunkIds.size());

        // 调用实际的向量化逻辑（非异步方法）
        vectorizeChunks(chunkIds);
    }

    /**
     * 拼接用于生成向量的 embeddingText（不含 category，不含 symptom_text）
     *
     * 格式：scene_title | 问法: questions | 说明: answer
     * 为空的片段整段跳过，避免出现孤立的 "问法: " 或首尾多余的 " | "。
     *
     * @param chunk 知识块实体
     * @return 拼接后的文本
     */
    String buildEmbeddingText(KnowledgeChunk chunk) {
        List<String> parts = new ArrayList<>();
        if (notBlank(chunk.getSceneTitle())) {
            parts.add(chunk.getSceneTitle().trim());
        }
        if (notBlank(chunk.getQuestions())) {
            parts.add("问法: " + chunk.getQuestions().trim());
        }
        if (notBlank(chunk.getAnswer())) {
            parts.add("说明: " + chunk.getAnswer().trim());
        }
        return String.join(" | ", parts);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * pgvector.text：全文检索字段 = doc_type + scene_title + questions + answer
     */
    public String buildSearchText(KnowledgeChunk chunk) {
        return joinNonBlank(chunk.getDocType(), chunk.getSceneTitle(), chunk.getQuestions(), chunk.getAnswer());
    }

    /**
     * pgvector.rerank_text：精排字段 = scene_title + questions + answer（不含 doc_type）
     */
    public String buildRerankText(KnowledgeChunk chunk) {
        return joinNonBlank(chunk.getSceneTitle(), chunk.getQuestions(), chunk.getAnswer());
    }

    /** 用单个空格连接非空片段，null/空白片段跳过，避免出现 "null" 或多余空格 */
    private static String joinNonBlank(String... parts) {
        return Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    /**
     * 将float[]转换为PostgreSQL向量字符串格式
     * 格式：[0.1,0.2,0.3,...]
     *
     * @param embedding 向量数组
     * @return PostgreSQL向量字符串
     */
    private String formatEmbedding(float[] embedding) {
        return Arrays.toString(embedding); // 输出格式：[0.1, 0.2, 0.3]
    }

    /**
     * 将ChunkDTO转换为KnowledgeChunk实体
     * 初始状态：pending_vectorization
     *
     * @param dto ChunkDTO
     * @return KnowledgeChunk实体
     */
    private KnowledgeChunk toEntity(ChunkDTO dto) {
        KnowledgeChunk entity = new KnowledgeChunk();
        entity.setDocType(dto.getDocType());
        entity.setDocId(dto.getDocId());
        entity.setCategory(dto.getCategory());
        entity.setSceneType(dto.getSceneType());
        entity.setSceneTitle(dto.getSceneTitle());
        entity.setSymptomText(dto.getSymptomText());
        entity.setQuestions(dto.getQuestions());
        entity.setAnswer(dto.getAnswer());
        entity.setSectionPath(dto.getSectionPath());
        entity.setContentType(dto.getContentType());
        entity.setIsTunable(dto.getIsTunable() != null ? dto.getIsTunable() : false);
        entity.setRouteAction(dto.getRouteAction());
        entity.setVectorizeStatus("pending_vectorization"); // 初始状态

        // 设置时间戳
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        // prev_chunk_id 和 next_chunk_id 暂不处理
        return entity;
    }
}
