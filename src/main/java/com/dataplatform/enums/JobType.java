package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 作业类型枚举
 */
@Getter
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

    public static JobType fromCode(String code) {
        for (JobType type : JobType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid JobType code: " + code);
    }
}
