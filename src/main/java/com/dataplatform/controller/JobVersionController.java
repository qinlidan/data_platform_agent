package com.dataplatform.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.SqlConfigRequest;
import com.dataplatform.entity.JobVersion;
import com.dataplatform.service.JobVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 作业版本控制器
 */
@Tag(name = "作业版本管理")
@RestController
@RequestMapping("/api/job-versions")
@RequiredArgsConstructor
public class JobVersionController {

    private final JobVersionService jobVersionService;

    @Operation(summary = "配置SQL")
    @PostMapping("/{jobId}/configure-sql")
    public ApiResponse<JobVersion> configureSql(@PathVariable Long jobId,
                                               @Valid @RequestBody SqlConfigRequest request) {
        return ApiResponse.success(jobVersionService.configureSql(jobId, request));
    }

    @Operation(summary = "查询作业的所有版本")
    @GetMapping("/job/{jobId}")
    public ApiResponse<List<JobVersion>> listByJobId(@PathVariable Long jobId) {
        return ApiResponse.success(jobVersionService.listByJobId(jobId));
    }

    @Operation(summary = "查询版本详情")
    @GetMapping("/{id}")
    public ApiResponse<JobVersion> getById(@PathVariable Long id) {
        return ApiResponse.success(jobVersionService.getById(id));
    }

    @Operation(summary = "发布版本")
    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        jobVersionService.publish(id);
        return ApiResponse.success();
    }

    @Operation(summary = "删除版本")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobVersionService.delete(id);
        return ApiResponse.success();
    }
}
