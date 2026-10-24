package com.dataplatform.auth.service;

import com.dataplatform.auth.dto.AuthResponse;
import com.dataplatform.auth.dto.LoginRequest;
import com.dataplatform.auth.dto.RegisterRequest;
import com.dataplatform.auth.dto.ResetPasswordRequest;
import com.dataplatform.auth.dto.SendCodeRequest;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 发送验证码
     */
    void sendCode(SendCodeRequest request);

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     */
    AuthResponse login(LoginRequest request);

    /**
     * 重置密码
     */
    void resetPassword(ResetPasswordRequest request);
}
