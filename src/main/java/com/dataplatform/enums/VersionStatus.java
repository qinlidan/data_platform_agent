package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 版本状态枚举
 *
 * <p>版本生命周期：
 * DRAFT → SUBMITTED → PUBLISHED → (ARCHIVED/ROLLBACK)
 */
@Getter
public enum VersionStatus {

    /**
     * 草稿 - 开发中，可随意修改
     */
    DRAFT("DRAFT", "草稿"),

    /**
     * 已提交 - 等待审批发布
     */
    SUBMITTED("SUBMITTED", "已提交"),

    /**
     * 已发布 - 线上运行版本
     */
    PUBLISHED("PUBLISHED", "已发布");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    VersionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 判断是否可以提交
     */
    public boolean canSubmit() {
        return this == DRAFT;
    }

    /**
     * 判断是否可以发布
     */
    public boolean canPublish() {
        return this == DRAFT || this == SUBMITTED;
    }

    /**
     * 判断是否可以编辑
     */
    public boolean canEdit() {
        return this == DRAFT;
    }

    /**
     * 判断是否可以回滚到此版本
     */
    public boolean canRollbackTo() {
        return this == PUBLISHED;
    }

    public static VersionStatus fromCode(String code) {
        for (VersionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的版本状态: " + code);
    }
}