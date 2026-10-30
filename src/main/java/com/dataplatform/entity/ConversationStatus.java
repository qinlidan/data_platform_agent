package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 会话状态枚举
 */
public enum ConversationStatus {
    /**
     * 进行中
     */
    ACTIVE("active", "进行中"),

    /**
     * 已结束
     */
    CLOSED("closed", "已结束"),

    /**
     * 已删除
     */
    DELETED("deleted", "已删除");

    @EnumValue  // MyBatis-Plus 存储到数据库时使用这个值
    @JsonValue  // JSON 序列化时使用这个值
    private final String code;

    private final String description;

    ConversationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
