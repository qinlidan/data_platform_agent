package com.dataplatform.dto.version;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建新版本请求
 */
@Data
@Schema(description = "创建新版本请求")
public class CreateVersionRequest {

    @NotNull(message = "作业ID不能为空")
    @Schema(description = "作业ID", example = "1")
    private Long jobId;

    @Schema(description = "变更日志", example = "修复数据过滤条件")
    private String changeLog;

    @Schema(description = "SQL内容（为空则复制当前版本）")
    private String sqlContent;

    @Schema(description = "Spark配置（JSON格式）")
    private String sparkConfig;

    @Schema(description = "是否基于已发布版本创建", example = "false")
    private Boolean basedOnPublished = false;
}
