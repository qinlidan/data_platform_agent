package com.dataplatform.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.FieldMetadataRequest;
import com.dataplatform.entity.FieldMetadata;
import com.dataplatform.service.FieldMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字段元数据控制器
 */
@Tag(name = "字段元数据管理")
@RestController
@RequestMapping("/api/field-metadata")
@RequiredArgsConstructor
public class FieldMetadataController {

    private final FieldMetadataService fieldMetadataService;

    @Operation(summary = "批量保存字段元数据")
    @PostMapping("/batch")
    public ApiResponse<Void> batchSave(@Valid @RequestBody FieldMetadataRequest.BatchSaveRequest request) {
        fieldMetadataService.batchSave(request.getJobVersionId(), request.getFields());
        return ApiResponse.success();
    }

    @Operation(summary = "查询版本的字段元数据")
    @GetMapping("/version/{jobVersionId}")
    public ApiResponse<List<FieldMetadata>> listByVersionId(@PathVariable Long jobVersionId) {
        return ApiResponse.success(fieldMetadataService.listByVersionId(jobVersionId));
    }

    @Operation(summary = "从SQL解析字段")
    @PostMapping("/parse")
    public ApiResponse<List<String>> parseFieldsFromSql(@RequestBody String sql) {
        return ApiResponse.success(fieldMetadataService.parseFieldsFromSql(sql));
    }
}
