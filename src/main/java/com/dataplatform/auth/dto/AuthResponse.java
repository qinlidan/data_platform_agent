package com.dataplatform.auth.dto;

import lombok.Data;

/**
 * 认证响应
 */
@Data
public class AuthResponse {

    /**
     * JWT Token
     */
    private String token;

    /**
     * 是否需要验证码
     */
    private Boolean needCode;
}
