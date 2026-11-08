package com.dataplatform.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.knowledge.*;
import com.dataplatform.service.KnowledgeImportService;
import com.dataplatform.service.KnowledgeManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 知识库管理控制器
 * SDD 第7节 - 知识库管理界面
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeManagementController {

    private final KnowledgeManagementService knowledgeManagementService;
    private final KnowledgeImportService knowledgeImportService;

    /**
     * 1. 获取材料清单
     */
    @Operation(summary = "获取文档列表")
    @GetMapping("/documents")
    public ApiResponse<List<DocumentInfoDTO>> listDocuments() {
        List<DocumentInfoDTO> documents = knowledgeManagementService.listDocuments();
        return ApiResponse.success(documents);
    }

    /**
     * 2. 重新导入文档
     */
    @Operation(summary = "重新导入文档")
    @PostMapping("/documents/{docId}/reimport")
    public ApiResponse<ReimportResultDTO> reimportDocument(@PathVariable String docId) {
        ReimportResultDTO result = knowledgeManagementService.reimportDocument(docId);
        return ApiResponse.success(result);
    }

    /**
     * 3. 查看文档切块
     */
    @Operation(summary = "查看文档切块")
    @GetMapping("/documents/{docId}/chunks")
    public ApiResponse<ChunkPageDTO> listChunks(
            @PathVariable String docId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ChunkPageDTO chunks = knowledgeManagementService.listChunks(docId, page, size);
        return ApiResponse.success(chunks);
    }

    /**
     * 4. 手工录入预览切分
     */
    @Operation(summary = "预览切分结果")
    @PostMapping("/manual/preview")
    public ApiResponse<PreviewResultDTO> previewChunks(@Valid @RequestBody ManualInputRequest request) {
        PreviewResultDTO result = knowledgeManagementService.previewChunks(request);
        return ApiResponse.success(result);
    }

    /**
     * 5. 手工录入确认入库
     */
    @Operation(summary = "确认入库")
    @PostMapping("/manual/import")
    public ApiResponse<ImportResultDTO> importManualChunks(@Valid @RequestBody ManualImportRequest request) {
        ImportResultDTO result = knowledgeManagementService.importManualChunks(request);
        return ApiResponse.success(result);
    }

    /**
     * 6. 检索自测
     */
    @Operation(summary = "检索测试")
    @PostMapping("/search/test")
    public ApiResponse<List<SearchResultDTO>> searchTest(@Valid @RequestBody SearchTestRequest request) {
        List<SearchResultDTO> results = knowledgeManagementService.searchTest(request);
        return ApiResponse.success(results);
    }
}
