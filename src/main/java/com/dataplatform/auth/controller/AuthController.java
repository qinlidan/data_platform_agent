package com.dataplatform.auth.controller;

import com.dataplatform.auth.dto.AuthResponse;
import com.dataplatform.auth.dto.LoginRequest;
import com.dataplatform.auth.dto.RegisterRequest;
import com.dataplatform.auth.dto.ResetPasswordRequest;
import com.dataplatform.auth.dto.SendCodeRequest;
import com.dataplatform.auth.service.AuthService;
import com.dataplatform.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送验证码
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request);
        return Result.success();
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /**
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 游客登录（演示模式）
     */
    @PostMapping("/guest-login")
    public Result<AuthResponse> guestLogin() {
        log.info("游客登录请求");
        AuthResponse response = authService.guestLogin();
        return Result.success(response);
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }
}
