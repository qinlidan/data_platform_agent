package com.dataplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SQL 生成请求
 */
@Data
@Schema(description = "SQL 生成请求")
public class SqlGenerationRequest {

    @Schema(description = "用户需求描述", example = "查询所有订单，按金额降序排列")
    private String requirement;

    @Schema(description = "数据库类型", example = "MySQL")
    private String databaseType;

    @Schema(description = "表名", example = "orders")
    private String tableName;

    @Schema(description = "表结构信息（可选）", example = "id, order_no, amount, create_time")
    private String tableSchema;
}
