package com.dataplatform.knowledge.controller;

import com.dataplatform.common.Result;
import com.dataplatform.knowledge.dto.HybridSearchResult;
import com.dataplatform.knowledge.service.HybridSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 混合检索 API
 *
 * Task 3 接口实现
 */
@RestController
@RequestMapping("/api/knowledge/hybrid")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "混合检索", description = "向量检索 + 关键词检索 + RRF 融合")
public class HybridSearchController {

    private final HybridSearchService hybridSearchService;

    /**
     * 混合检索（向量 + 关键词 + RRF 融合）
     */
    @GetMapping("/search")
    @Operation(summary = "混合检索", description = "向量检索 + 关键词检索 + RRF 融合")
    public Result<List<HybridSearchResult>> hybridSearch(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "文档类型过滤（可选）") @RequestParam(required = false) String docType,
            @Parameter(description = "返回top-k结果数量（默认10）") @RequestParam(defaultValue = "10") int topK
    ) {
        log.info("混合检索请求 - query: {}, docType: {}, topK: {}", query, docType, topK);

        List<HybridSearchResult> results = hybridSearchService.hybridSearch(query, docType, topK);

        return Result.success(results);
    }

    /**
     * 纯向量检索（用于评估对比）
     */
    @GetMapping("/vector")
    @Operation(summary = "纯向量检索", description = "仅使用向量检索（用于评估对比）")
    public Result<List<HybridSearchResult>> vectorSearch(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "文档类型过滤（可选）") @RequestParam(required = false) String docType,
            @Parameter(description = "返回top-k结果数量（默认10）") @RequestParam(defaultValue = "10") int topK
    ) {
        log.info("纯向量检索请求 - query: {}, docType: {}, topK: {}", query, docType, topK);

        List<HybridSearchResult> results = hybridSearchService.vectorSearch(query, docType, topK);

        return Result.success(results);
    }

    /**
     * 纯关键词检索（用于评估对比）
     */
    @GetMapping("/keyword")
    @Operation(summary = "纯关键词检索", description = "仅使用关键词检索（用于评估对比）")
    public Result<List<HybridSearchResult>> keywordSearch(
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "文档类型过滤（可选）") @RequestParam(required = false) String docType,
            @Parameter(description = "返回top-k结果数量（默认10）") @RequestParam(defaultValue = "10") int topK
    ) {
        log.info("纯关键词检索请求 - query: {}, docType: {}, topK: {}", query, docType, topK);

        List<HybridSearchResult> results = hybridSearchService.keywordSearch(query, docType, topK);

        return Result.success(results);
    }
}
