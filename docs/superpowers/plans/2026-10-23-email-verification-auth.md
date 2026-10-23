# 邮箱验证登录功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现邮箱验证码登录系统，支持注册、登录、忘记密码三个场景，7天内免验证码快速登录。

**Architecture:** 后端使用 Spring Boot + Spring Mail + JWT + Redis，前端使用 Vue3 + Element Plus。验证码通过 163 SMTP 发送，存储在 Redis 中（10分钟过期），JWT Token 固定7天有效期。

**Tech Stack:** Spring Boot 3.2.2, Java 17, Spring Mail, jjwt 0.12.x, Redis, BCrypt, Vue 3, Element Plus, Axios

**Spec:** `docs/superpowers/specs/2026-10-23-email-verification-auth-design.md`

## Global Constraints

- Spring Boot 版本: 3.2.2
- Java 版本: 17
- 验证码格式: 6位数字
- 验证码有效期: 10分钟（600秒）
- JWT Token 有效期: 7天（604800000毫秒）
- 防刷限制: 同一邮箱1分钟内只能发送1次验证码
- 密码加密: BCrypt（强度10）
- 邮件服务: 163 SMTP (smtp.163.com:465 SSL)
- 密码强度: 至少8位，包含字母和数字

## Review Focus

1. **验证码过期未清理**: 验证码过期后必须从 Redis 删除，避免用户使用过期验证码登录
2. **并发注册冲突**: 两个用户同时注册同一邮箱时，数据库唯一索引会抛异常，必须捕获并返回友好错误
3. **邮件发送失败**: SMTP 不可用时，必须返回明确错误，不能存储验证码到 Redis
4. **Token 伪造**: JWT 必须使用强密钥签名，防止攻击者伪造 Token
5. **密码明文传输**: 前端必须通过 HTTPS 传输密码（生产环境），开发环境需提示

---

## 文件结构

### 后端新增文件

**认证相关：**
- `src/main/java/com/dataplatform/auth/dto/SendCodeRequest.java` - 发送验证码请求
- `src/main/java/com/dataplatform/auth/dto/RegisterRequest.java` - 注册请求
- `src/main/java/com/dataplatform/auth/dto/LoginRequest.java` - 登录请求
- `src/main/java/com/dataplatform/auth/dto/ResetPasswordRequest.java` - 重置密码请求
- `src/main/java/com/dataplatform/auth/dto/AuthResponse.java` - 认证响应（包含 Token）
- `src/main/java/com/dataplatform/auth/entity/User.java` - 用户实体
- `src/main/java/com/dataplatform/auth/mapper/UserMapper.java` - 用户 Mapper
- `src/main/java/com/dataplatform/auth/service/AuthService.java` - 认证服务接口
- `src/main/java/com/dataplatform/auth/service/impl/AuthServiceImpl.java` - 认证服务实现
- `src/main/java/com/dataplatform/auth/service/VerificationCodeService.java` - 验证码服务
- `src/main/java/com/dataplatform/auth/service/EmailService.java` - 邮件服务
- `src/main/java/com/dataplatform/auth/controller/AuthController.java` - 认证控制器
- `src/main/java/com/dataplatform/auth/enums/CodeType.java` - 验证码类型枚举
- `src/main/java/com/dataplatform/auth/exception/AuthException.java` - 认证异常

**JWT 相关：**
- `src/main/java/com/dataplatform/auth/util/JwtUtil.java` - JWT 工具类
- `src/main/java/com/dataplatform/auth/config/JwtProperties.java` - JWT 配置属性
- `src/main/java/com/dataplatform/auth/filter/JwtAuthenticationFilter.java` - JWT 认证过滤器

**配置文件：**
- 修改 `src/main/resources/application.yml` - 添加邮件和 JWT 配置

**数据库迁移：**
- `src/main/resources/db/migration/V1__create_user_table.sql` - 创建用户表

### 前端新增文件

**页面组件：**
- `frontend/src/views/auth/Login.vue` - 登录页面
- `frontend/src/views/auth/Register.vue` - 注册页面
- `frontend/src/views/auth/ResetPassword.vue` - 重置密码页面

**API 封装：**
- `frontend/src/api/auth.js` - 认证相关 API

**工具函数：**
- `frontend/src/composables/useAuth.js` - 认证逻辑 Hook
- `frontend/src/utils/token.js` - Token 存储工具

**路由配置：**
- 修改 `frontend/src/router/index.js` - 添加认证路由和路由守卫

---

## Task 1: 数据库表和实体类

**Files:**
- Create: `src/main/resources/db/migration/V1__create_user_table.sql`
- Create: `src/main/java/com/dataplatform/auth/entity/User.java`
- Create: `src/main/java/com/dataplatform/auth/mapper/UserMapper.java`

**Interfaces:**
- Consumes: 无
- Produces: 
  - `User` 实体类，字段：`id:Long`, `email:String`, `password:String`, `lastVerifiedAt:LocalDateTime`, `status:Integer`, `createTime:LocalDateTime`, `updateTime:LocalDateTime`
  - `UserMapper` 接口，方法：继承 `BaseMapper<User>`

- [ ] **Step 1: 创建数据库迁移 SQL**

创建文件 `src/main/resources/db/migration/V1__create_user_table.sql`:

```sql
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `last_verified_at` DATETIME NULL COMMENT '最后一次邮箱验证时间',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1=正常 0=禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_email` (`email`),
  INDEX `idx_last_verified_at` (`last_verified_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

- [ ] **Step 2: 执行数据库迁移**

运行命令：`mysql -h localhost -u root -p alert4 < src/main/resources/db/migration/V1__create_user_table.sql`

预期：表创建成功，执行 `SHOW TABLES LIKE 'user';` 能看到表

- [ ] **Step 3: 创建 User 实体类**

创建文件 `src/main/java/com/dataplatform/auth/entity/User.java`:

```java
package com.dataplatform.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String email;
    private String password;
    private LocalDateTime lastVerifiedAt;
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

- [ ] **Step 4: 创建 UserMapper 接口**

创建文件 `src/main/java/com/dataplatform/auth/mapper/UserMapper.java`:

```java
package com.dataplatform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

- [ ] **Step 5: 编译验证**

运行：`cd D:/alert4 && mvn compile`

预期：编译成功，无错误

- [ ] **Step 6: 提交**

```bash
git add src/main/resources/db/migration/V1__create_user_table.sql src/main/java/com/dataplatform/auth/entity/User.java src/main/java/com/dataplatform/auth/mapper/UserMapper.java
git commit -m "feat(auth): 添加用户表和实体类

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 2: DTO 和枚举类

**Files:**
- Create: `src/main/java/com/dataplatform/auth/dto/SendCodeRequest.java`
- Create: `src/main/java/com/dataplatform/auth/dto/RegisterRequest.java`
- Create: `src/main/java/com/dataplatform/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/dataplatform/auth/dto/ResetPasswordRequest.java`
- Create: `src/main/java/com/dataplatform/auth/dto/AuthResponse.java`
- Create: `src/main/java/com/dataplatform/auth/enums/CodeType.java`
- Create: `src/main/java/com/dataplatform/auth/exception/AuthException.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `SendCodeRequest(email:String, type:CodeType)`
  - `RegisterRequest(email:String, password:String, code:String)`
  - `LoginRequest(email:String, password:String, code:String)`
  - `ResetPasswordRequest(email:String, code:String, newPassword:String)`
  - `AuthResponse(token:String, userId:Long, email:String)`
  - `CodeType` 枚举：REGISTER, LOGIN, RESET_PASSWORD
  - `AuthException extends RuntimeException`

- [ ] **Step 1: 创建验证码类型枚举**

创建文件 `src/main/java/com/dataplatform/auth/enums/CodeType.java`:

```java
package com.dataplatform.auth.enums;

public enum CodeType {
    REGISTER,
    LOGIN,
    RESET_PASSWORD
}
```

- [ ] **Step 2: 创建发送验证码请求 DTO**

创建文件 `src/main/java/com/dataplatform/auth/dto/SendCodeRequest.java`:

```java
package com.dataplatform.auth.dto;

import com.dataplatform.auth.enums.CodeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendCodeRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotNull(message = "验证码类型不能为空")
    private CodeType type;
}
```

- [ ] **Step 3: 创建注册请求 DTO**

创建文件 `src/main/java/com/dataplatform/auth/dto/RegisterRequest.java`:

```java
package com.dataplatform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$", 
             message = "密码至少8位，包含字母和数字")
    private String password;
    
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;
}
```

- [ ] **Step 4: 创建登录请求 DTO**

创建文件 `src/main/java/com/dataplatform/auth/dto/LoginRequest.java`:

```java
package com.dataplatform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;  // 可选
}
```

- [ ] **Step 5: 创建重置密码请求 DTO**

创建文件 `src/main/java/com/dataplatform/auth/dto/ResetPasswordRequest.java`:

```java
package com.dataplatform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;
    
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$", 
             message = "密码至少8位，包含字母和数字")
    private String newPassword;
}
```

- [ ] **Step 6: 创建认证响应 DTO**

创建文件 `src/main/java/com/dataplatform/auth/dto/AuthResponse.java`:

```java
package com.dataplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String email;
}
```

- [ ] **Step 7: 创建认证异常类**

创建文件 `src/main/java/com/dataplatform/auth/exception/AuthException.java`:

```java
package com.dataplatform.auth.exception;

public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
    
    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 8: 编译验证**

运行：`mvn compile`

预期：编译成功

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/dataplatform/auth/dto/ src/main/java/com/dataplatform/auth/enums/ src/main/java/com/dataplatform/auth/exception/
git commit -m "feat(auth): 添加 DTO 和枚举类

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 3: JWT 工具类和配置

**Files:**
- Create: `src/main/java/com/dataplatform/auth/config/JwtProperties.java`
- Create: `src/main/java/com/dataplatform/auth/util/JwtUtil.java`
- Modify: `pom.xml` (添加 jjwt 依赖)
- Modify: `src/main/resources/application.yml` (添加 JWT 配置)

**Interfaces:**
- Consumes: `User` 实体（Task 1）
- Produces:
  - `JwtUtil.generateToken(userId:Long, email:String):String`
  - `JwtUtil.validateToken(token:String):boolean`
  - `JwtUtil.getUserIdFromToken(token:String):Long`
  - `JwtUtil.getEmailFromToken(token:String):String`

- [ ] **Step 1: 添加 jjwt 依赖到 pom.xml**

在 `<dependencies>` 中添加：

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: 创建 JWT 配置属性类**

创建文件 `src/main/java/com/dataplatform/auth/config/JwtProperties.java`:

```java
package com.dataplatform.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private Long expiration;  // 毫秒
}
```

- [ ] **Step 3: 添加 JWT 配置到 application.yml**

在 `src/main/resources/application.yml` 末尾添加：

```yaml
# JWT 配置
jwt:
  secret: ${JWT_SECRET:dataplatform-secret-key-min-256-bits-for-production-use}
  expiration: 604800000  # 7天（毫秒）
```

- [ ] **Step 4: 创建 JWT 工具类**

创建文件 `src/main/java/com/dataplatform/auth/util/JwtUtil.java`:

```java
package com.dataplatform.auth.util;

import com.dataplatform.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    
    private final JwtProperties jwtProperties;
    
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration());
        
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSignKey())
                .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);
    }
    
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("email", String.class);
    }
}
```

- [ ] **Step 5: 编译验证**

运行：`mvn compile`

预期：编译成功

- [ ] **Step 6: 单元测试 JWT 工具类**

创建测试文件（可选，但推荐）验证 token 生成和解析

- [ ] **Step 7: 提交**

```bash
git add pom.xml src/main/java/com/dataplatform/auth/config/JwtProperties.java src/main/java/com/dataplatform/auth/util/JwtUtil.java src/main/resources/application.yml
git commit -m "feat(auth): 添加 JWT 工具类和配置

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 4: 邮件服务

**Files:**
- Create: `src/main/java/com/dataplatform/auth/service/EmailService.java`
- Modify: `pom.xml` (添加 Spring Mail 依赖)
- Modify: `src/main/resources/application.yml` (添加邮件配置)

**Interfaces:**
- Consumes: `CodeType` 枚举（Task 2）
- Produces:
  - `EmailService.sendVerificationCode(email:String, code:String, type:CodeType):void`

- [ ] **Step 1: 添加 Spring Mail 依赖到 pom.xml**

在 `<dependencies>` 中添加：

```xml
<!-- Spring Mail -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

- [ ] **Step 2: 添加邮件配置到 application.yml**

在 `src/main/resources/application.yml` 中添加：

```yaml
spring:
  mail:
    host: smtp.163.com
    port: 465
    username: 17766238993@163.com
    password: ${MAIL_PASSWORD:ZRYM339mZrztCZyA}
    protocol: smtp
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true
            required: true
    default-encoding: UTF-8
```

- [ ] **Step 3: 创建邮件服务类**

创建文件 `src/main/java/com/dataplatform/auth/service/EmailService.java`:

```java
package com.dataplatform.auth.service;

import com.dataplatform.auth.enums.CodeType;
import com.dataplatform.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    public void sendVerificationCode(String email, String code, CodeType type) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("17766238993@163.com");
            message.setTo(email);
            message.setSubject(getSubject(type));
            message.setText(getContent(code, type));
            
            mailSender.send(message);
            log.info("验证码邮件发送成功: email={}, type={}", email, type);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: email={}, type={}", email, type, e);
            throw new AuthException("验证码发送失败，请稍后重试", e);
        }
    }
    
    private String getSubject(CodeType type) {
        return switch (type) {
            case REGISTER -> "【数据平台】注册验证码";
            case LOGIN -> "【数据平台】登录验证码";
            case RESET_PASSWORD -> "【数据平台】重置密码验证码";
        };
    }
    
    private String getContent(String code, CodeType type) {
        String action = switch (type) {
            case REGISTER -> "注册数据平台账号";
            case LOGIN -> "登录数据平台";
            case RESET_PASSWORD -> "重置数据平台密码";
        };
        
        return String.format("""
                您好！
                
                您正在%s，验证码为：
                
                    %s
                
                验证码10分钟内有效，请勿泄露给他人。
                
                如非本人操作，请忽略此邮件。
                """, action, code);
    }
}
```

- [ ] **Step 4: 编译验证**

运行：`mvn compile`

预期：编译成功

- [ ] **Step 5: 提交**

```bash
git add pom.xml src/main/java/com/dataplatform/auth/service/EmailService.java src/main/resources/application.yml
git commit -m "feat(auth): 添加邮件服务

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 5: 验证码服务（Redis）

**Files:**
- Create: `src/main/java/com/dataplatform/auth/service/VerificationCodeService.java`
- Modify: `pom.xml` (添加 Redis 依赖)
- Modify: `src/main/resources/application.yml` (添加 Redis 配置)

**Interfaces:**
- Consumes: `CodeType` 枚举（Task 2）
- Produces:
  - `VerificationCodeService.generateCode():String`
  - `VerificationCodeService.saveCode(email:String, code:String, type:CodeType):void`
  - `VerificationCodeService.validateCode(email:String, code:String, type:CodeType):boolean`
  - `VerificationCodeService.canSendCode(email:String):boolean`
  - `VerificationCodeService.recordSendTime(email:String):void`

- [ ] **Step 1: 添加 Redis 依赖到 pom.xml**

在 `<dependencies>` 中添加：

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **Step 2: 添加 Redis 配置到 application.yml**

```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    database: 0
    timeout: 3000ms
```

- [ ] **Step 3: 创建验证码服务类**

创建文件 `src/main/java/com/dataplatform/auth/service/VerificationCodeService.java`:

```java
package com.dataplatform.auth.service;

import com.dataplatform.auth.enums.CodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final long CODE_EXPIRE_MINUTES = 10;
    private static final long SEND_INTERVAL_SECONDS = 60;
    
    public String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }
    
    public void saveCode(String email, String code, CodeType type) {
        String key = getCodeKey(email, type);
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.info("验证码已保存: email={}, type={}", email, type);
    }
    
    public boolean validateCode(String email, String code, CodeType type) {
        String key = getCodeKey(email, type);
        String savedCode = redisTemplate.opsForValue().get(key);
        
        if (savedCode == null) {
            log.warn("验证码不存在或已过期: email={}, type={}", email, type);
            return false;
        }
        
        if (!savedCode.equals(code)) {
            log.warn("验证码错误: email={}, type={}", email, type);
            return false;
        }
        
        // 验证成功后删除验证码
        redisTemplate.delete(key);
        log.info("验证码验证成功: email={}, type={}", email, type);
        return true;
    }
    
    public boolean canSendCode(String email) {
        String key = getSendLimitKey(email);
        return Boolean.FALSE.equals(redisTemplate.hasKey(key));
    }
    
    public void recordSendTime(String email) {
        String key = getSendLimitKey(email);
        redisTemplate.opsForValue().set(key, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private String getCodeKey(String email, CodeType type) {
        return String.format("email_code:%s:%s", type, email);
    }
    
    private String getSendLimitKey(String email) {
        return String.format("email_code_limit:%s", email);
    }
}
```

- [ ] **Step 4: 编译验证