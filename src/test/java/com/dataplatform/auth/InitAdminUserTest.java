package com.dataplatform.auth;

import com.dataplatform.auth.entity.User;
import com.dataplatform.auth.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

@SpringBootTest
public class InitAdminUserTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    public void initAdminUser() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        // 检查是否已存在
        User existing = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, "admin@alert.com")
        );
        if (existing != null) {
            System.out.println("Admin user already exists: " + existing.getEmail());
            return;
        }

        // 创建管理员账号
        User admin = new User();
        admin.setEmail("admin@alert.com");
        admin.setPassword(encoder.encode("280976"));
        admin.setStatus(1);
        admin.setLastVerifiedAt(LocalDateTime.now());

        userMapper.insert(admin);
        System.out.println("✓ Admin user created successfully!");
        System.out.println("  Username: admin");
        System.out.println("  Email: admin@alert.com");
        System.out.println("  Password: 280976");
    }
}
