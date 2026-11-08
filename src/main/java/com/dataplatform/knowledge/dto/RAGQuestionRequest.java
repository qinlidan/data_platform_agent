package com.dataplatform.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * RAG 问答请求
 */
@Data
@Schema(description = "RAG 问答请求")
public class RAGQuestionRequest {

    @Schema(description = "用户问题", required = true, example = "如何配置数据源？")
    private String query;

    @Schema(description = "对话 ID", example = "123")
    private Long conversationId;

    @Schema(description = "向量相似度阈值", example = "0.3", defaultValue = "0.3")
    private Double vectorSimThreshold = 0.3;

    @Schema(description = "重排阈值", example = "0.5", defaultValue = "0.5")
    private Double rerankThreshold = 0.5;
}
