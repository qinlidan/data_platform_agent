package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 执行引擎类型枚举
 */
public enum EngineType {

    HIVE("HIVE", "Hive引擎"),
    SPARK("SPARK", "Spark引擎");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    EngineType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static EngineType fromCode(String code) {
        for (EngineType type : EngineType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid EngineType code: " + code);
    }
}
