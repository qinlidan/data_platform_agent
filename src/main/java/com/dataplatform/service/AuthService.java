package com.dataplatform.service;

import com.dataplatform.dto.LoginRequest;
import com.dataplatform.dto.LoginResponse;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出
     */
    void logout();
}
