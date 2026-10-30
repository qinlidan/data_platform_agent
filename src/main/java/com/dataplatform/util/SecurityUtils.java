package com.dataplatform.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.auth.entity.User;
import com.dataplatform.auth.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 安全工具类 - 获取当前登录用户信息
 */
@Component
public class SecurityUtils {

    private static UserMapper userMapper;

    // 通过构造函数注入（Spring会自动调用）
    public SecurityUtils(UserMapper userMapper) {
        SecurityUtils.userMapper = userMapper;
    }

    /**
     * 获取当前用户邮箱（作为用户标识）
     *
     * @return 用户邮箱，未登录返回 "anonymous"
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }

        Object principal = authentication.getPrincipal();

        // 如果是 UserDetails 实现类
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }

        // 其他类型，直接转字符串
        return principal.toString();
    }

    /**
     * 获取当前用户的数字ID（从数据库查询）
     *
     * @return 用户ID（Long），未登录返回 null
     */
    public static Long getCurrentUserIdAsLong() {
        String email = getCurrentUserId();
        if ("anonymous".equals(email)) {
            return null;
        }

        // 从数据库查询用户ID
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );

        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户ID，未登录抛出异常
     *
     * @return 用户ID
     * @throws IllegalStateException 未登录或未认证
     */
    public static String requireCurrentUserId() {
        String userId = getCurrentUserId();
        if ("anonymous".equals(userId)) {
            throw new IllegalStateException("未登录或未认证");
        }
        return userId;
    }

    /**
     * 获取当前用户的数字ID，未登录抛出异常
     *
     * @return 用户ID（Long）
     * @throws IllegalStateException 未登录或未认证
     */
    public static Long requireCurrentUserIdAsLong() {
        Long userId = getCurrentUserIdAsLong();
        if (userId == null) {
            throw new IllegalStateException("未登录或未认证");
        }
        return userId;
    }
}
