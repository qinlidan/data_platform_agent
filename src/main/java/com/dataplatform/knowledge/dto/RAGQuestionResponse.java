package com.dataplatform.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * RAG 问答响应
 */
@Data
@Builder
@Schema(description = "RAG 问答响应")
public class RAGQuestionResponse {

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "是否拒答")
    private Boolean refused;

    @Schema(description = "拒答原因")
    private String refusalReason;
}
