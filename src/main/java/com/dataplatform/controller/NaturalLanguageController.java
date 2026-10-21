package com.dataplatform.controller;

import com.dataplatform.common.ApiResponse;
import com.dataplatform.dto.JobOperationRequest;
import com.dataplatform.dto.JobOperationResponse;
import com.dataplatform.service.NaturalLanguageJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 自然语言作业管理控制器
 */
@Tag(name = "自然语言作业管理")
@RestController
@RequestMapping("/api/nl")
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageController {

    private final NaturalLanguageJobService naturalLanguageJobService;

    @Operation(summary = "自然语言对话")
    @PostMapping("/chat")
    public ApiResponse<JobOperationResponse> chat(@Valid @RequestBody JobOperationRequest request) {
        log.info("收到自然语言对话请求: {}", request.getUserInput());
        try {
            JobOperationResponse response = naturalLanguageJobService.chat(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("自然语言对话失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "自然语言创建作业")
    @PostMapping("/job/create")
    public ApiResponse<JobOperationResponse> createJob(@Valid @RequestBody JobOperationRequest request) {
        log.info("收到创建作业请求: {}", request.getUserInput());
        try {
            JobOperationResponse response = naturalLanguageJobService.createJob(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("创建作业失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "自然语言修改作业")
    @PostMapping("/job/update")
    public ApiResponse<JobOperationResponse> updateJob(@Valid @RequestBody JobOperationRequest request) {
        log.info("收到修改作业请求: {}", request.getUserInput());
        try {
            JobOperationResponse response = naturalLanguageJobService.updateJob(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("修改作业失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "自然语言发布作业")
    @PostMapping("/job/publish")
    public ApiResponse<JobOperationResponse> publishJob(@Valid @RequestBody JobOperationRequest request) {
        log.info("收到发布作业请求: {}", request.getUserInput());
        try {
            JobOperationResponse response = naturalLanguageJobService.publishJob(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("发布作业失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @Operation(summary = "自然语言删除作业")
    @PostMapping("/job/delete")
    public ApiResponse<JobOperationResponse> deleteJob(@Valid @RequestBody JobOperationRequest request) {
        log.info("收到删除作业请求: {}", request.getUserInput());
        try {
            JobOperationResponse response = naturalLanguageJobService.deleteJob(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("删除作业失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
