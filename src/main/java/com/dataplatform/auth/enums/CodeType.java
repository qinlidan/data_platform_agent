package com.dataplatform.auth.enums;

import lombok.Getter;

@Getter
public enum CodeType {
    REGISTER("注册"),
    LOGIN("登录");

    private final String description;

    CodeType(String description) {
        this.description = description;
    }
}
