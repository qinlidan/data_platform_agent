package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 检索测试请求
 */
@Data
@Schema(description = "检索测试请求")
public class SearchTestRequest {

    @NotBlank(message = "查询内容不能为空")
    @Schema(description = "查询内容")
    private String query;

    @Schema(description = "文档类型过滤（可选）")
    private String docType;

    @Schema(description = "返回结果数量")
    private Integer topK;
}
