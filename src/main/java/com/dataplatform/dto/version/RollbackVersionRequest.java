package com.dataplatform.dto.version;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 版本回滚请求
 */
@Data
@Schema(description = "版本回滚请求")
public class RollbackVersionRequest {

    @NotNull(message = "作业ID不能为空")
    @Schema(description = "作业ID", example = "1")
    private Long jobId;

    @NotNull(message = "目标版本号不能为空")
    @Schema(description = "目标版本号（回滚到哪个版本）", example = "2")
    private Integer targetVersion;

    @NotBlank(message = "回滚原因不能为空")
    @Schema(description = "回滚原因", example = "数据异常，回滚到上一稳定版本")
    private String rollbackReason;

    @Schema(description = "是否需要审批", example = "true")
    private Boolean requireApproval = true;
}
