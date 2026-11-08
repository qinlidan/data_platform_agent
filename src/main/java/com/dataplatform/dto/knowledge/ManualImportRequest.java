package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 手工导入请求
 */
@Data
@Schema(description = "手工导入请求")
public class ManualImportRequest {

    @NotBlank(message = "文档类型不能为空")
    @Schema(description = "文档类型")
    private String docType;

    @NotEmpty(message = "至少选择一个切块")
    @Schema(description = "选中的临时ID列表")
    private List<String> selectedTempIds;

    @Schema(description = "切块数据列表")
    private List<PreviewResultDTO.PreviewChunkDTO> chunks;
}
