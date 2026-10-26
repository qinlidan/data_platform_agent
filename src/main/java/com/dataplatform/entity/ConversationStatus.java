package com.dataplatform.entity;

/**
 * 会话状态枚举
 */
public enum ConversationStatus {
    /**
     * 进行中
     */
    ACTIVE("进行中"),

    /**
     * 已结束
     */
    CLOSED("已结束"),

    /**
     * 已删除
     */
    DELETED("已删除");

    private final String description;

    ConversationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
