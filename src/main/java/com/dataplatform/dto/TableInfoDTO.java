package com.dataplatform.dto;

import lombok.Data;
import java.util.List;

/**
 * 表信息DTO（含字段明细）
 */
@Data
public class TableInfoDTO {
    /** 表名 */
    private String tableName;

    /** 表中文描述 */
    private String tableComment;

    /** 字段列表 */
    private List<TableFieldDTO> fields;
}
