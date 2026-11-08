package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 检索结果DTO
 */
@Data
@Schema(description = "检索结果")
public class SearchResultDTO {

    @Schema(description = "切块ID")
    private Long chunkId;

    @Schema(description = "问法")
    private String questions;

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "相似度分数")
    private Double score;

    @Schema(description = "文档类型")
    private String docType;

    @Schema(description = "文档ID")
    private String docId;

    @Schema(description = "章节路径")
    private String sectionPath;
}
