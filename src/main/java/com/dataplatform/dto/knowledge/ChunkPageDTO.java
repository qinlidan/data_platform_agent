package com.dataplatform.dto.knowledge;

import com.dataplatform.dto.ChunkDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 切块分页DTO
 */
@Data
@Schema(description = "切块分页结果")
public class ChunkPageDTO {

    @Schema(description = "总数")
    private Long total;

    @Schema(description = "切块列表")
    private List<ChunkItemDTO> chunks;

    @Data
    @Schema(description = "切块项")
    public static class ChunkItemDTO {
        @Schema(description = "切块ID")
        private Long id;

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

        @Schema(description = "向量化状态")
        private String vectorizeStatus;
    }
}
