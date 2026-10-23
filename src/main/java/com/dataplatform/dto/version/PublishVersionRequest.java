package com.dataplatform.dto.version;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 发布版本请求
 */
@Data
@Schema(description = "发布版本请求")
public class PublishVersionRequest {

    @NotNull(message = "作业ID不能为空")
    @Schema(description = "作业ID", example = "1")
    private Long jobId;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "版本号", example = "2")
    private Integer version;

    @Schema(description = "发布备注", example = "修复数据质量问题，紧急发布")
    private String remark;

    @Schema(description = "是否需要审批", example = "false")
    private Boolean requireApproval = false;
}
