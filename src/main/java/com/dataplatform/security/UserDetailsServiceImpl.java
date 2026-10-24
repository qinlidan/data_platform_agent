package com.dataplatform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.auth.entity.User;
import com.dataplatform.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 用户详情服务实现
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 查询用户（这里的 username 参数实际上是 email）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email)
        );

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + email);
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户已被禁用: " + email);
        }

        // 构建Spring Security的UserDetails对象
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(user.getStatus() == 0)
                .build();
    }
}
