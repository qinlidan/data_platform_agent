package com.dataplatform.dto.version;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 版本比较响应
 */
@Data
@Builder
@Schema(description = "版本比较结果")
public class VersionCompareVO {

    @Schema(description = "作业ID")
    private Long jobId;

    @Schema(description = "版本1号")
    private Integer version1;

    @Schema(description = "版本2号")
    private Integer version2;

    @Schema(description = "版本1状态")
    private String version1Status;

    @Schema(description = "版本2状态")
    private String version2Status;

    @Schema(description = "差异项列表")
    private List<DiffItem> differences;

    @Schema(description = "是否有差异")
    private Boolean hasDifferences;

    /**
     * 差异项
     */
    @Data
    @Builder
    public static class DiffItem {
        @Schema(description = "字段名称")
        private String fieldName;

        @Schema(description = "字段显示名")
        private String fieldDisplayName;

        @Schema(description = "版本1的值")
        private String value1;

        @Schema(description = "版本2的值")
        private String value2;

        @Schema(description = "是否有差异")
        private Boolean isDifferent;
    }
}
