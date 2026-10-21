package com.dataplatform.dto;

import lombok.Data;

/**
 * 表字段DTO
 */
@Data
public class TableFieldDTO {
    /** 字段名 */
    private String fieldName;

    /** 字段类型 */
    private String fieldType;

    /** 字段中文描述 */
    private String fieldComment;

    /** 是否主键 */
    private Boolean isPrimaryKey;

    /** 是否可为空 */
    private Boolean isNullable;
}
