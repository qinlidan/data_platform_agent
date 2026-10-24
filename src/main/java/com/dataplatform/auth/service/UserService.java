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
