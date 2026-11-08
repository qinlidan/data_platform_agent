package com.dataplatform.service;

import com.dataplatform.dto.SearchResultDTO;
import com.dataplatform.entity.KnowledgeChunk;
import com.dataplatform.knowledge.dto.VectorSearchResult;
import com.dataplatform.knowledge.repository.KnowledgeVectorRepository;
import com.dataplatform.knowledge.service.EmbeddingService;
import com.dataplatform.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索服务
 * Task 7 - 向量检索服务
 *
 * 功能：
 * 1. 调用EmbeddingService生成查询向量
 * 2. 调用KnowledgeVectorRepository执行向量相似度检索
 * 3. 从KnowledgeChunkMapper批量查询元数据
 * 4. 组装SearchResultDTO并保持pgvector的排序
 *
 * @author Task 7
 * @date 2026-11-01
 */
@Service
@Slf4j
public class VectorSearchService {

    private final EmbeddingService embeddingService;
    private final KnowledgeVectorRepository vectorRepository;
    private final KnowledgeChunkMapper chunkMapper;

    public VectorSearchService(
        @Qualifier("knowledgeEmbeddingService") EmbeddingService embeddingService,
        KnowledgeVectorRepository vectorRepository,
        KnowledgeChunkMapper chunkMapper
    ) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.chunkMapper = chunkMapper;
    }

    /**
     * 向量语义检索
     *
     * @param query 用户查询文本
     * @param topK 返回top-K结果
     * @return 包含chunk文本和元数据的结果列表，按相似度降序排列
     */
    public List<SearchResultDTO> search(String query, int topK) {
        return searchWithDTO(query, topK);
    }

    /**
     * 向量语义检索（支持docType过滤，返回Map格式）
     *
     * @param query 用户查询文本
     * @param docType 文档类型过滤（可选）
     * @param topK 返回top-K结果
     * @return 包含chunk文本和元数据的结果列表，按相似度降序排列
     */
    public List<Map<String, Object>> searchWithMap(String query, String docType, int topK) {
        log.info("开始向量检索: query={}, docType={}, topK={}", query, docType, topK);

        // Step 1: 查询向量化（添加 query: 前缀）
        float[] queryEmbedding = embeddingService.embedQuery(query);
        log.debug("查询向量化完成: dimension={}", queryEmbedding.length);

        // Step 2: 转换向量为pgvector格式字符串
        String embeddingStr = arrayToString(queryEmbedding);

        // Step 3: pgvector检索
        List<VectorSearchResult> vectorResults = vectorRepository.searchSimilar(
            embeddingStr,
            docType,
            topK
        );

        if (vectorResults.isEmpty()) {
            log.info("向量检索无结果");
            return Collections.emptyList();
        }

        log.info("向量检索返回 {} 条结果", vectorResults.size());

        // Step 4: 提取chunk_id列表（保持顺序）
        List<Long> chunkIds = vectorResults.stream()
            .map(VectorSearchResult::getChunkId)
            .collect(Collectors.toList());

        // Step 5: 批量查询MySQL元数据
        List<KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds);

        // Step 6: 建立chunk_id -> KnowledgeChunk 的映射
        Map<Long, KnowledgeChunk> chunkMap = chunks.stream()
            .collect(Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk));

        // Step 7: 组装结果，保持pgvector返回的顺序
        List<Map<String, Object>> results = new ArrayList<>();
        for (VectorSearchResult vectorResult : vectorResults) {
            Long chunkId = vectorResult.getChunkId();
            KnowledgeChunk chunk = chunkMap.get(chunkId);

            if (chunk == null) {
                log.warn("找不到chunk元数据: chunk_id={}", chunkId);
                continue;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", chunkId);
            result.put("questions", chunk.getQuestions());
            result.put("answer", chunk.getAnswer());
            result.put("score", vectorResult.getScore().doubleValue());
            result.put("docType", chunk.getDocType());
            result.put("docId", chunk.getDocId());
            result.put("sectionPath", chunk.getSectionPath());

            results.add(result);
        }

        log.info("检索完成: 返回 {} 条结果", results.size());
        return results;
    }

    /**
     * 向量语义检索（返回SearchResultDTO）
     *
     * @param query 用户查询文本
     * @param topK 返回top-K结果
     * @return SearchResultDTO列表
     */
    public List<SearchResultDTO> searchWithDTO(String query, int topK) {
        log.info("开始向量检索: query={}, topK={}", query, topK);

        // Step 1: 查询向量化（添加 query: 前缀）
        float[] queryEmbedding = embeddingService.embedQuery(query);
        log.debug("查询向量化完成: dimension={}", queryEmbedding.length);

        // Step 2: 转换向量为pgvector格式字符串
        String embeddingStr = arrayToString(queryEmbedding);

        // Step 3: pgvector检索（不过滤doc_type）
        List<VectorSearchResult> vectorResults = vectorRepository.searchSimilar(
            embeddingStr,
            null,  // docType过滤，本期不使用
            topK
        );

        if (vectorResults.isEmpty()) {
            log.info("向量检索无结果");
            return Collections.emptyList();
        }

        log.info("向量检索返回 {} 条结果", vectorResults.size());

        // Step 4: 提取chunk_id列表（保持顺序）
        List<Long> chunkIds = vectorResults.stream()
            .map(VectorSearchResult::getChunkId)
            .collect(Collectors.toList());

        // Step 5: 批量查询MySQL元数据
        List<KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds);

        // Step 6: 建立chunk_id -> KnowledgeChunk 的映射
        Map<Long, KnowledgeChunk> chunkMap = chunks.stream()
            .collect(Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk));

        // Step 7: 组装SearchResultDTO，保持pgvector返回的顺序
        List<SearchResultDTO> results = new ArrayList<>();
        for (VectorSearchResult vectorResult : vectorResults) {
            Long chunkId = vectorResult.getChunkId();
            KnowledgeChunk chunk = chunkMap.get(chunkId);

            if (chunk == null) {
                log.warn("找不到chunk元数据: chunk_id={}", chunkId);
                continue;
            }

            SearchResultDTO dto = new SearchResultDTO();
            dto.setChunkId(chunkId);
            dto.setChunkText(chunk.getQuestions());  // questions作为主标题
            dto.setChunkType(chunk.getSceneType());
            dto.setDocType(chunk.getDocType());
            dto.setDocId(chunk.getDocId());
            dto.setSimilarity(vectorResult.getScore().floatValue());
            dto.setAnswer(chunk.getAnswer());
            dto.setSectionPath(chunk.getSectionPath());
            dto.setRouteAction(vectorResult.getRouteAction());

            results.add(dto);
        }

        log.info("检索完成: 返回 {} 条结果", results.size());
        return results;
    }

    /**
     * 将float数组转换为pgvector格式字符串
     * 格式：[0.1, 0.2, 0.3, ...]
     *
     * @param array float数组
     * @return pgvector格式字符串
     */
    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
