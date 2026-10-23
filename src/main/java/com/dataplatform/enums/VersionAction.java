package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 版本操作类型枚举
 */
@Getter
public enum VersionAction {

    /**
     * 创建版本
     */
    CREATE("CREATE", "创建版本"),

    /**
     * 编辑版本
     */
    EDIT("EDIT", "编辑版本"),

    /**
     * 提交审核
     */
    SUBMIT("SUBMIT", "提交审核"),

    /**
     * 发布版本
     */
    PUBLISH("PUBLISH", "发布版本"),

    /**
     * 回滚版本
     */
    ROLLBACK("ROLLBACK", "回滚版本"),

    /**
     * 归档版本
     */
    ARCHIVE("ARCHIVE", "归档版本"),

    /**
     * 下线作业
     */
    OFFLINE("OFFLINE", "下线作业");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    VersionAction(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static VersionAction fromCode(String code) {
        for (VersionAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知的操作类型: " + code);
    }
}