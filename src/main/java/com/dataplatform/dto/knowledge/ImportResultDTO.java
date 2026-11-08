package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 导入结果DTO
 */
@Data
@Schema(description = "导入结果")
public class ImportResultDTO {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "消息")
    private String message;

    @Schema(description = "导入的切块数量")
    private Integer importedCount;
}
