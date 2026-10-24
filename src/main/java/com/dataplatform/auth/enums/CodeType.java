package com.dataplatform.auth.enums;

import lombok.Getter;

@Getter
public enum CodeType {
    REGISTER("注册"),
    LOGIN("登录"),
    RESET_PASSWORD("重置密码");

    private final String description;

    CodeType(String description) {
        this.description = description;
    }
}
