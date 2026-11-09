package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 作业类型枚举
 */
public enum JobType {

    DB2HIVE("DB2HIVE", "数据库到Hive"),
    HIVE2HIVE("HIVE2HIVE", "Hive到Hive");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    JobType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static JobType fromCode(String code) {
        for (JobType type : JobType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid JobType code: " + code);
    }
}
