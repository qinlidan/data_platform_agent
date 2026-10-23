package com.dataplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 字段元数据请求DTO
 */
@Data
public class FieldMetadataRequest {

    @NotBlank(message = "字段名不能为空")
    private String fieldName;

    @NotBlank(message = "字段类型不能为空")
    private String fieldType;

    private String fieldComment;

    private Boolean isPrimaryKey;

    private Boolean isPartitionKey;

    private Boolean isNullable;

    private String defaultValue;

    /**
     * 批量保存字段元数据
     */
    @Data
    public static class BatchSaveRequest {
        @NotNull(message = "作业版本ID不能为空")
        private Long jobVersionId;

        private List<FieldMetadataRequest> fields;
    }
}
