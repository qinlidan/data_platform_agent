package com.dataplatform.enums;

/**
 * 数据类型枚举
 */
public enum DataType {

    // 数值类型
    TINYINT,
    SMALLINT,
    INT,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL,

    // 字符串类型
    STRING,
    VARCHAR,
    CHAR,
    TEXT,

    // 日期时间类型
    DATE,
    TIMESTAMP,
    DATETIME,

    // 布尔类型
    BOOLEAN,

    // 二进制类型
    BINARY,
    VARBINARY,

    // 复杂类型
    ARRAY,
    MAP,
    STRUCT
}
