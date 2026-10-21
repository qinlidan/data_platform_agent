package com.dataplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.DataSourceRequest;
import com.dataplatform.entity.DataSource;
import com.dataplatform.service.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源控制器
 */
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @Operation(summary = "创建数据源")
    @PostMapping
    public ApiResponse<DataSource> create(@Valid @RequestBody DataSourceRequest request) {
        return ApiResponse.success(dataSourceService.create(request));
    }

    @Operation(summary = "更新数据源")
    @PutMapping("/{id}")
    public ApiResponse<DataSource> update(@PathVariable Long id,
                                         @Valid @RequestBody DataSourceRequest request) {
        return ApiResponse.success(dataSourceService.update(id, request));
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "查询数据源详情")
    @GetMapping("/{id}")
    public ApiResponse<DataSource> getById(@PathVariable Long id) {
        return ApiResponse.success(dataSourceService.getById(id));
    }

    @Operation(summary = "分页查询数据源列表")
    @GetMapping
    public ApiResponse<Page<DataSource>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(dataSourceService.page(pageNum, pageSize, keyword));
    }

    @Operation(summary = "查询所有数据源")
    @GetMapping("/all")
    public ApiResponse<List<DataSource>> listAll() {
        return ApiResponse.success(dataSourceService.listAll());
    }

    @Operation(summary = "根据数据源名称查询ID")
    @GetMapping("/id-by-name")
    public ApiResponse<Long> getIdByName(@RequestParam String sourceName) {
        return ApiResponse.success(dataSourceService.getIdByName(sourceName));
    }

    @Operation(summary = "测试数据源连接")
    @PostMapping("/{id}/test")
    public ApiResponse<Boolean> testConnection(@PathVariable Long id) {
        return ApiResponse.success(dataSourceService.testConnection(id));
    }

    @Operation(summary = "根据参数测试数据源连接")
    @PostMapping("/test-connection")
    public ApiResponse<Boolean> testConnectionByParams(@RequestBody DataSourceRequest request) {
        return ApiResponse.success(dataSourceService.testConnection(request));
    }
}
