package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 手工录入请求
 */
@Data
@Schema(description = "手工录入请求")
public class ManualInputRequest {

    @NotBlank(message = "Markdown文本不能为空")
    @Schema(description = "Markdown文本")
    private String markdownText;

    @NotBlank(message = "文档类型不能为空")
    @Schema(description = "文档类型")
    private String docType;
}
