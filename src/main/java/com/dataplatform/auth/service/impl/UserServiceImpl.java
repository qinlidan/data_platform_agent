package com.dataplatform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.auth.entity.User;
import com.dataplatform.auth.mapper.UserMapper;
import com.dataplatform.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public User findByEmail(String email) {
        return userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );
    }

    @Override
    public User findByUsernameOrEmail(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isEmpty()) {
            return null;
        }

        // 如果包含@，按邮箱查询
        if (usernameOrEmail.contains("@")) {
            return findByEmail(usernameOrEmail);
        }

        // 否则尝试多种匹配方式
        // 1. 先尝试按 ID 查询（用于 admin 这样的特殊账号）
        User user = userMapper.selectById(usernameOrEmail);
        if (user != null) {
            return user;
        }

        // 2. 尝试匹配邮箱前缀（@之前的部分）
        return userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .likeRight(User::getEmail, usernameOrEmail + "@")
                .last("LIMIT 1")
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String email, String encryptedPassword) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(encryptedPassword);
        user.setStatus(1); // 正常状态
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        log.info("创建用户成功: email={}, userId={}", email, user.getId());
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastVerifiedAt(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setLastVerifiedAt(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(user);
        log.info("更新最后验证时间: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, String encryptedPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(encryptedPassword);
        user.setLastVerifiedAt(null); // 清除验证时间，下次登录需重新验证
        user.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(user);
        log.info("更新密码成功: userId={}", userId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        ) > 0;
    }
}
