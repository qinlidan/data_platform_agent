package com.dataplatform.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.TableInfoDTO;
import com.dataplatform.service.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 元数据管理接口（Mock版）
 */
@Tag(name = "元数据管理")
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    /**
     * 获取指定数据源的库列表
     */
    @Operation(summary = "获取库列表")
    @GetMapping("/databases")
    public ApiResponse<List<String>> listDatabases(@RequestParam Long datasourceId) {
        return ApiResponse.success(metadataService.listDatabases(datasourceId));
    }

    /**
     * 获取指定库的表列表（含字段信息）
     */
    @Operation(summary = "获取表列表")
    @GetMapping("/tables")
    public ApiResponse<List<TableInfoDTO>> listTables(
            @RequestParam Long datasourceId,
            @RequestParam String database) {
        return ApiResponse.success(metadataService.listTables(datasourceId, database));
    }
}
