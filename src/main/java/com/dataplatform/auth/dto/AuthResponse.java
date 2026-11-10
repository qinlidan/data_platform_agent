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

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色（admin, guest, user）
     */
    private String role;
}
