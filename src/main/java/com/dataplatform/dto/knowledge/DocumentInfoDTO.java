package com.dataplatform.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档信息DTO
 */
@Data
@Schema(description = "文档信息")
public class DocumentInfoDTO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文档ID")
    private String docId;

    @Schema(description = "文档类型")
    private String docType;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastModified;

    @Schema(description = "导入时间")
    private LocalDateTime importTime;

    @Schema(description = "切块数量")
    private Integer chunkCount;
}
