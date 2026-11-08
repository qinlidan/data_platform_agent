package com.dataplatform.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import com.dataplatform.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge/vectorization")
@Slf4j
public class KnowledgeVectorizationController {

    private final KnowledgeChunkMapper chunkMapper;
    private final VectorizationService vectorizationService;

    public KnowledgeVectorizationController(KnowledgeChunkMapper chunkMapper,
                                             VectorizationService vectorizationService) {
        this.chunkMapper = chunkMapper;
        this.vectorizationService = vectorizationService;
    }

    @PostMapping("/retry")
    public Map<String, Object> retryVectorization(@RequestBody(required = false) Map<String, List<Long>> request) {
        List<Long> chunkIds = request != null ? request.get("chunkIds") : null;

        if (chunkIds == null || chunkIds.isEmpty()) {
            chunkIds = chunkMapper.selectList(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getVectorizeStatus, "pending_vectorization")
            ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
        }

        log.info("手动触发向量化补偿任务: {} chunks", chunkIds.size());
        vectorizationService.vectorizeChunksAsync(chunkIds);

        return Map.of("submitted", chunkIds.size(), "message", "已提交补偿任务");
    }

    /**
     * 存量回填：对所有已向量化的 chunk 重新走一遍向量化，补写 text / rerank_text / section_path。
     * 与 /retry 的区别：/retry 只处理 pending_vectorization，而存量数据状态已是 vectorized。
     * 会重新调用 Embedding 接口（有费用/耗时），按需手动触发。
     */
    @PostMapping("/backfill")
    public Map<String, Object> backfillHybridColumns() {
        List<Long> chunkIds = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getVectorizeStatus, "vectorized")
        ).stream().map(KnowledgeChunk::getId).collect(Collectors.toList());

        log.info("手动触发混合检索列回填: {} chunks", chunkIds.size());
        vectorizationService.vectorizeChunksAsync(chunkIds);

        return Map.of("submitted", chunkIds.size(), "message", "已提交回填任务");
    }
}
