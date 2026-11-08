package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 预览结果DTO
 */
@Data
@Schema(description = "切分预览结果")
public class PreviewResultDTO {

    @Schema(description = "切块列表")
    private List<PreviewChunkDTO> chunks;

    @Data
    @Schema(description = "预览切块")
    public static class PreviewChunkDTO {
        @Schema(description = "临时ID（用于前端勾选）")
        private String tempId;

        @Schema(description = "问法")
        private String questions;

        @Schema(description = "答案")
        private String answer;

        @Schema(description = "场景类型")
        private String sceneType;

        @Schema(description = "场景标题")
        private String sceneTitle;

        @Schema(description = "章节路径")
        private String sectionPath;

        @Schema(description = "是否重复")
        private Boolean isDuplicate;

        @Schema(description = "相似度（如果重复）")
        private Double similarity;
    }
}
