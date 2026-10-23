package com.dataplatform.dto.version;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 版本回滚请求DTO
 */
@Data
@Builder
public class RollbackRequest {

    /**
     * 作业ID
     */
    @NotNull(message = "作业ID不能为空")
    private Long jobId;

    /**
     * 目标版本号（要回滚到的版本）
     */
    @NotNull(message = "目标版本号不能为空")
    @Min(value = 1, message = "版本号必须大于0")
    private Integer targetVersion;

    /**
     * 回滚原因（必填）
     */
    @NotBlank(message = "回滚原因不能为空")
    private String reason;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;
}
