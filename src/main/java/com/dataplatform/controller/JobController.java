package com.dataplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.JobCreateRequest;
import com.dataplatform.dto.JobQueryRequest;
import com.dataplatform.dto.JobUpdateRequest;
import com.dataplatform.entity.Job;
import com.dataplatform.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 作业控制器
 */
@Tag(name = "作业管理")
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "创建作业")
    @PostMapping
    public ApiResponse<Job> create(@Valid @RequestBody JobCreateRequest request) {
        return ApiResponse.success(jobService.create(request));
    }

    @Operation(summary = "更新作业")
    @PutMapping("/{id}")
    public ApiResponse<Job> update(@PathVariable Long id,
                                  @Valid @RequestBody JobUpdateRequest request) {
        return ApiResponse.success(jobService.update(id, request));
    }

    @Operation(summary = "删除作业")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "查询作业详情")
    @GetMapping("/{id}")
    public ApiResponse<Job> getById(@PathVariable Long id) {
        return ApiResponse.success(jobService.getById(id));
    }

    @Operation(summary = "分页查询作业列表")
    @GetMapping
    public ApiResponse<Page<Job>> page(JobQueryRequest request) {
        return ApiResponse.success(jobService.page(request));
    }

    @Operation(summary = "发布作业")
    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id,
                                    @RequestParam Integer version) {
        jobService.publish(id, version);
        return ApiResponse.success();
    }

    @Operation(summary = "归档作业")
    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        jobService.archive(id);
        return ApiResponse.success();
    }
}
