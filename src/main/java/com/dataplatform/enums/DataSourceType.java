package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 数据源类型枚举
 */
@Getter
public enum DataSourceType {

    MYSQL("MYSQL", "MySQL数据库"),
    HIVE("HIVE", "Hive数据仓库"),
    ORACLE("ORACLE", "Oracle数据库"),
    POSTGRESQL("POSTGRESQL", "PostgreSQL数据库"),
    SQLSERVER("SQLSERVER", "SQL Server数据库"),
    CLICKHOUSE("CLICKHOUSE", "ClickHouse数据库"),
    HBASE("HBASE", "HBase数据库"),
    DB2("DB2", "DB2数据库");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    DataSourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static DataSourceType fromCode(String code) {
        for (DataSourceType type : DataSourceType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid DataSourceType code: " + code);
    }
}
