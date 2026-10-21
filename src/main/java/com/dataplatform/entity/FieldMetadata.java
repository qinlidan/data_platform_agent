package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段元数据实体
 */
@Data
@TableName("etl_field_metadata")
public class FieldMetadata {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobVersionId;

    private String fieldName;

    private String fieldType;

    private String fieldComment;

    private Integer isPartition;

    private Integer isPrimary;

    private Integer isNullable;

    private String defaultValue;

    private Integer fieldOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
