package com.dataplatform.service.impl;

import com.dataplatform.dto.LoginRequest;
import com.dataplatform.dto.LoginResponse;
import com.dataplatform.entity.User;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.mapper.UserMapper;
import com.dataplatform.service.AuthService;
import com.dataplatform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现类
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 生成JWT Token
        String token = jwtUtil.generateToken(user.getUsername());

        return new LoginResponse(token, user.getUsername(), user.getNickname());
    }

    @Override
    public void logout() {
        // JWT无状态，logout逻辑可在前端处理（删除token）
        // 如需实现token黑名单，可在此处添加
    }
}
