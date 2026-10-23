package com.dataplatform.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 审批状态枚举
 */
@Getter
public enum ApprovalStatus {

    /**
     * 待审批
     */
    PENDING("PENDING", "待审批"),

    /**
     * 已通过
     */
    APPROVED("APPROVED", "已通过"),

    /**
     * 已驳回
     */
    REJECTED("REJECTED", "已驳回"),

    /**
     * 已取消
     */
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    ApprovalStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isApproved() {
        return this == APPROVED;
    }

    public static ApprovalStatus fromCode(String code) {
        for (ApprovalStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的审批状态: " + code);
    }
}