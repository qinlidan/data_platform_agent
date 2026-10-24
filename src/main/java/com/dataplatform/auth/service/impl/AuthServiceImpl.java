package com.dataplatform.auth.service.impl;

import com.dataplatform.auth.dto.AuthResponse;
import com.dataplatform.auth.dto.LoginRequest;
import com.dataplatform.auth.dto.RegisterRequest;
import com.dataplatform.auth.dto.ResetPasswordRequest;
import com.dataplatform.auth.dto.SendCodeRequest;
import com.dataplatform.auth.entity.User;
import com.dataplatform.auth.enums.CodeType;
import com.dataplatform.auth.service.AuthService;
import com.dataplatform.auth.service.EmailService;
import com.dataplatform.auth.service.UserService;
import com.dataplatform.auth.service.VerificationCodeService;
import com.dataplatform.auth.util.JwtUtil;
import com.dataplatform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final VerificationCodeService codeService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    private static final int VERIFICATION_VALID_DAYS = 7;

    @Override
    public void sendCode(SendCodeRequest request) {
        String email = request.getEmail();
        CodeType type = request.getType();

        // 根据类型执行不同的校验
        switch (type) {
            case REGISTER -> {
                if (userService.existsByEmail(email)) {
                    throw new BusinessException("邮箱已被注册");
                }
            }
            case LOGIN, RESET_PASSWORD -> {
                if (!userService.existsByEmail(email)) {
                    throw new BusinessException("邮箱未注册");
                }
            }
        }

        // 防重发检查（原子操作）
        if (!codeService.tryRecordSendTime(email)) {
            throw new BusinessException("请1分钟后再试");
        }

        // 生成并存储验证码
        String code = codeService.generateCode();
        codeService.saveCode(email, code, type);

        // 发送邮件
        emailService.sendVerificationCode(email, code, type);

        log.info("验证码发送成功: email={}, type={}", email, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        String password = request.getPassword();

        // 验证码校验
        if (!codeService.validateCode(email, code, CodeType.REGISTER)) {
            throw new BusinessException("验证码错误或已过期");
        }

        // 检查邮箱是否已注册
        if (userService.existsByEmail(email)) {
            throw new BusinessException("邮箱已被注册");
        }

        // 密码加密
        String encryptedPassword = passwordEncoder.encode(password);

        // 创建用户
        try {
            userService.createUser(email, encryptedPassword);
            log.info("用户注册成功: email={}", email);
        } catch (DuplicateKeyException e) {
            // 处理并发注册冲突
            throw new BusinessException("邮箱已被注册");
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();
        String code = request.getCode();

        // 查询用户
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new BusinessException("邮箱或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("邮箱或密码错误");
        }

        // 检查账号状态
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // 判断是否需要验证码
        boolean needCode = isVerificationRequired(user.getLastVerifiedAt());

        if (needCode) {
            // 需要验证码但未提供
            if (code == null || code.trim().isEmpty()) {
                AuthResponse response = new AuthResponse();
                response.setNeedCode(true);
                return response;
            }

            // 验证码校验
            if (!codeService.validateCode(email, code, CodeType.LOGIN)) {
                throw new BusinessException("验证码错误或已过期");
            }

            // 更新最后验证时间
            userService.updateLastVerifiedAt(user.getId());
            user.setLastVerifiedAt(LocalDateTime.now());
        }

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getLastVerifiedAt());

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setNeedCode(false);

        log.info("用户登录成功: email={}, needCode={}", email, needCode);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        String newPassword = request.getNewPassword();

        // 验证码校验
        if (!codeService.validateCode(email, code, CodeType.RESET_PASSWORD)) {
            throw new BusinessException("验证码错误或已过期");
        }

        // 查询用户
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new BusinessException("邮箱未注册");
        }

        // 密码加密
        String encryptedPassword = passwordEncoder.encode(newPassword);

        // 更新密码（会清除 lastVerifiedAt）
        userService.updatePassword(user.getId(), encryptedPassword);

        log.info("密码重置成功: email={}", email);
    }

    /**
     * 判断是否需要验证码
     * - lastVerifiedAt 为 null（首次登录）→ 需要
     * - 距离现在超过7天 → 需要
     * - 7天内 → 不需要
     */
    private boolean isVerificationRequired(LocalDateTime lastVerifiedAt) {
        if (lastVerifiedAt == null) {
            return true;
        }

        Duration duration = Duration.between(lastVerifiedAt, LocalDateTime.now());
        return duration.toDays() >= VERIFICATION_VALID_DAYS;
    }
}
