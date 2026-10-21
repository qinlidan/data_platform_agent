package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 作业状态枚举
 */
@Getter
public enum JobStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    ARCHIVED("ARCHIVED", "已归档");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    JobStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static JobStatus fromCode(String code) {
        for (JobStatus status : JobStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid JobStatus code: " + code);
    }
}
