package com.dataplatform.auth.service;

import com.dataplatform.auth.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 根据邮箱查询用户
     */
    User findByEmail(String email);

    /**
     * 根据用户名或邮箱查询用户
     * 如果输入包含@，按邮箱查询；否则尝试匹配邮箱前缀或ID
     */
    User findByUsernameOrEmail(String usernameOrEmail);

    /**
     * 创建新用户
     */
    User createUser(String email, String encryptedPassword);

    /**
     * 更新最后验证时间
     */
    void updateLastVerifiedAt(Long userId);

    /**
     * 更新密码
     */
    void updatePassword(Long userId, String encryptedPassword);

    /**
     * 检查邮箱是否已存在
     */
    boolean existsByEmail(String email);
}
