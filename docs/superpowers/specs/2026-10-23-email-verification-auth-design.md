# 邮箱验证登录功能设计文档

## 1. 意图与目标

### 为什么需要这个功能
数据平台需要一套安全的用户认证系统，通过邮箱验证码增强登录安全性，防止账号被盗用。用户首次登录或长时间未登录时需要邮箱验证，验证通过后7天内免验证码快速登录。

### 成功标准
- 用户能够通过邮箱注册新账号
- 首次登录或7天后登录需要邮箱验证码
- 7天内只需邮箱+密码即可登录
- 忘记密码时能通过邮箱验证码重置密码
- 验证码10分钟内有效
- 所有邮件能成功发送到用户邮箱

### 目标（Goals）
- 提供安全的邮箱验证登录机制
- 平衡安全性与用户体验（7天免验证码）
- 支持注册、登录、忘记密码三个场景
- 邮件发送稳定可靠

### 非目标（Non-Goals）
- 不支持手机号验证码登录
- 不支持第三方登录（微信、QQ等）
- 不支持双因素认证（2FA）
- 不做登录日志审计功能

### 反目标（Anti-Goals）
- 验证码太难用导致用户放弃注册
- 邮件发送失败率高影响用户体验
- Token 永不过期造成安全隐患
- 验证码可被暴力破解

## 2. 约束条件

### 技术约束
- 后端：Spring Boot 3.2.2 + Java 17
- 前端：Vue 3 + Element Plus + Vite
- 数据库：MySQL 5.7（已有）
- 邮件服务：163 SMTP（smtp.163.com:465 SSL）
  - 发件人：17766238993@163.com
  - 授权码：ZRYM339mZrztCZyA

### 业务约束
- 验证码10分钟有效期
- JWT Token 7天固定有效期（不滑动）
- 验证码为6位数字
- 同一邮箱1分钟内只能发送1次验证码（防刷）

## 3. 功能详细设计

### 3.1 注册流程

**前端交互：**
- 页面：独立的"注册"入口
- 表单字段：
  - 邮箱（email，必填，邮箱格式校验）
  - 密码（password，必填，至少6位）
  - 确认密码（confirmPassword，必填，与密码一致）
  - 验证码（code，必填，6位数字）
  - "发送验证码"按钮（60秒倒计时）

**后端流程：**
1. 用户点击"发送验证码"
   - 调用：`POST /api/auth/send-code`
   - 参数：`{"email": "xxx", "type": "REGISTER"}`
   - 校验邮箱格式
   - 检查邮箱是否已注册（已注册返回错误）
   - 检查1分钟内是否已发送（已发送返回错误）
   - 生成6位随机数字验证码
   - 存储到 Redis：key=`email_code:REGISTER:{email}`，value=`{code}`，TTL=10分钟
   - 发送邮件到用户邮箱
   - 返回成功

2. 用户填写表单并提交注册
   - 调用：`POST /api/auth/register`
   - 参数：`{"email": "xxx", "password": "xxx", "code": "123456"}`
   - 校验参数格式
   - 从 Redis 获取验证码并校验（不匹配返回错误，匹配后删除）
   - 检查邮箱是否已注册
   - 密码 BCrypt 加密
   - 插入数据库：`user` 表
   - 返回成功

### 3.2 登录流程

**前端交互：**
- 页面：独立的"登录"入口
- 表单字段：
  - 邮箱（email，必填）
  - 密码（password，必填）
  - 验证码（code，条件显示）
  - "发送验证码"按钮（条件显示）
  - "忘记密码"链接

**后端流程：**
1. 用户输入邮箱+密码，点击登录
   - 调用：`POST /api/auth/login`
   - 参数：`{"email": "xxx", "password": "xxx", "code": "123456"}`（code 可选）
   
2. 后端判断是否需要验证码：
   - 从数据库查询用户
   - 验证密码（BCrypt）
   - 检查用户的 `last_verified_at` 字段：
     - 如果为 null（首次登录）→ 需要验证码
     - 如果距离现在超过7天 → 需要验证码
     - 如果7天内 → 不需要验证码
   
3. 如果需要验证码但未提供：
   - 返回：`{"code": 40001, "message": "需要邮箱验证码", "needCode": true}`
   - 前端收到后显示验证码输入框和"发送验证码"按钮
   
4. 用户点击"发送验证码"：
   - 调用：`POST /api/auth/send-code`
   - 参数：`{"email": "xxx", "type": "LOGIN"}`
   - 生成验证码并发送邮件（流程同注册）
   
5. 用户输入验证码后重新提交登录：
   - 后端校验验证码
   - 验证通过后：
     - 更新 `last_verified_at = NOW()`
     - 生成 JWT Token（包含：email, userId, lastVerifiedAt，有效期7天）
     - 返回 Token
   
6. 如果不需要验证码：
   - 直接生成 JWT Token
   - 返回 Token

### 3.3 忘记密码流程

**前端交互：**
- 页面：独立的"忘记密码"入口
- 步骤1：输入邮箱，发送验证码
- 步骤2：输入验证码 + 新密码 + 确认密码

**后端流程：**
1. 用户输入邮箱并请求验证码
   - 调用：`POST /api/auth/send-code`
   - 参数：`{"email": "xxx", "type": "RESET_PASSWORD"}`
   - 检查邮箱是否存在（不存在返回错误）
   - 生成验证码并发送邮件

2. 用户输入验证码和新密码
   - 调用：`POST /api/auth/reset-password`
   - 参数：`{"email": "xxx", "code": "123456", "newPassword": "xxx"}`
   - 校验验证码
   - 新密码 BCrypt 加密
   - 更新数据库 `user.password`
   - 清除该用户的 `last_verified_at`（下次登录需要重新验证）
   - 返回成功

### 3.4 登录状态维护

**JWT Token 设计：**
```json
{
  "userId": 123,
  "email": "user@example.com",
  "lastVerifiedAt": "2026-10-23T10:30:00Z",
  "iat": 1729680600,
  "exp": 1730285400
}
```

**有效期管理：**
- Token 生成时 `exp` = 当前时间 + 7天
- 7天后 Token 失效，前端跳转到登录页
- 用户重新登录时判断是否需要验证码

**前端 Token 存储：**
- 存储位置：`localStorage.setItem('token', token)`
- 请求拦截器：所有 API 请求携带 `Authorization: Bearer <token>`
- 响应拦截器：401 状态码清除 Token 并跳转登录页

### 3.5 验证码统一发送接口

**接口定义：**
```
POST /api/auth/send-code
Content-Type: application/json

{
  "email": "user@example.com",
  "type": "REGISTER" | "LOGIN" | "RESET_PASSWORD"
}
```

**业务逻辑：**
1. 校验参数：
   - email 格式
   - type 枚举值
   
2. 根据 type 执行不同校验：
   - REGISTER：邮箱不能已存在
   - LOGIN：邮箱必须存在，且密码已通过验证（需要先调用登录接口）
   - RESET_PASSWORD：邮箱必须存在

3. 防刷检查：
   - Redis key: `email_code_limit:{email}`
   - 如果存在且 TTL > 0：返回"请1分钟后再试"
   - 否则：设置 key，TTL=60秒

4. 生成并存储验证码：
   - 生成6位随机数字
   - Redis key: `email_code:{type}:{email}`
   - Value: `{code}`
   - TTL: 10分钟

5. 发送邮件：
   - 使用 Spring Mail + 163 SMTP
   - 邮件模板根据 type 不同：
     - REGISTER: "【数据平台】注册验证码"
     - LOGIN: "【数据平台】登录验证码"
     - RESET_PASSWORD: "【数据平台】重置密码验证码"
   - 邮件内容：验证码 + 有效期提示

## 4. 数据库设计

### 4.1 用户表（user）

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

**字段说明：**
- `email`: 唯一索引，用于登录和查找用户
- `password`: BCrypt 加密后的密码
- `last_verified_at`: 用于判断是否需要验证码（NULL 或超过7天）
- `status`: 预留字段，用于账号禁用

### 4.2 Redis 数据结构

**验证码存储：**
```
Key: email_code:{type}:{email}
Value: {code}
TTL: 600秒（10分钟）

示例：
email_code:REGISTER:user@example.com = "123456"
email_code:LOGIN:user@example.com = "654321"
email_code:RESET_PASSWORD:user@example.com = "789012"
```

**防刷限制：**
```
Key: email_code_limit:{email}
Value: 1
TTL: 60秒（1分钟）

示例：
email_code_limit:user@example.com = "1"
```

## 5. API 接口设计

### 5.1 发送验证码

```
POST /api/auth/send-code

Request:
{
  "email": "user@example.com",
  "type": "REGISTER" | "LOGIN" | "RESET_PASSWORD"
}

Response (成功):
{
  "code": 200,
  "message": "验证码已发送",
  "data": null
}

Response (失败):
{
  "code": 400,
  "message": "邮箱已注册" | "邮箱不存在" | "请1分钟后再试",
  "data": null
}
```

### 5.2 注册

```
POST /api/auth/register

Request:
{
  "email": "user@example.com",
  "password": "123456",
  "code": "123456"
}

Response (成功):
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 123,
    "email": "user@example.com"
  }
}

Response (失败):
{
  "code": 400,
  "message": "验证码错误" | "邮箱已注册" | "密码格式错误",
  "data": null
}
```

### 5.3 登录

```
POST /api/auth/login

Request:
{
  "email": "user@example.com",
  "password": "123456",
  "code": "123456"  // 可选
}

Response (成功):
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGc...",
    "userId": 123,
    "email": "user@example.com"
  }
}

Response (需要验证码):
{
  "code": 40001,
  "message": "需要邮箱验证码",
  "data": {
    "needCode": true
  }
}

Response (失败):
{
  "code": 400,
  "message": "邮箱或密码错误" | "验证码错误",
  "data": null
}
```

### 5.4 忘记密码

```
POST /api/auth/reset-password

Request:
{
  "email": "user@example.com",
  "code": "123456",
  "newPassword": "newpass123"
}

Response (成功):
{
  "code": 200,
  "message": "密码重置成功",
  "data": null
}

Response (失败):
{
  "code": 400,
  "message": "验证码错误" | "邮箱不存在",
  "data": null
}
```

### 5.5 登出（可选）

```
POST /api/auth/logout

Headers:
Authorization: Bearer <token>

Response:
{
  "code": 200,
  "message": "登出成功",
  "data": null
}
```

注：登出主要由前端清除 localStorage 中的 token，后端可不实现。

## 6. 前端页面设计

### 6.1 注册页面（Register.vue）

**布局：**
- 居中卡片
- 标题："用户注册"
- 表单字段：
  - 邮箱输入框
  - 密码输入框
  - 确认密码输入框
  - 验证码输入框 + "发送验证码"按钮（倒计时）
  - "注册"按钮
  - "已有账号？去登录"链接

**交互逻辑：**
1. 点击"发送验证码"：
   - 校验邮箱格式
   - 调用 `/api/auth/send-code`（type=REGISTER）
   - 成功后按钮禁用，显示倒计时"59秒后重试"
   - 失败显示错误提示

2. 点击"注册"：
   - 校验所有字段
   - 密码与确认密码一致性校验
   - 调用 `/api/auth/register`
   - 成功后跳转登录页

### 6.2 登录页面（Login.vue）

**布局：**
- 居中卡片
- 标题："用户登录"
- 表单字段：
  - 邮箱输入框
  - 密码输入框
  - 验证码输入框（条件显示）+ "发送验证码"按钮（条件显示）
  - "登录"按钮
  - "忘记密码"链接
  - "没有账号？去注册"链接

**交互逻辑：**
1. 首次提交：
   - 调用 `/api/auth/login`（不带 code）
   - 如果返回 `needCode: true`：
     - 显示验证码输入框和发送按钮
     - 提示用户"需要邮箱验证码"
   - 如果成功：
     - 保存 token 到 localStorage
     - 跳转到首页

2. 需要验证码时：
   - 点击"发送验证码"调用 `/api/auth/send-code`（type=LOGIN）
   - 输入验证码后重新提交登录

### 6.3 忘记密码页面（ResetPassword.vue）

**布局：**
- 居中卡片
- 标题："重置密码"
- 步骤1：
  - 邮箱输入框
  - "发送验证码"按钮（倒计时）
- 步骤2（收到验证码后显示）：
  - 验证码输入框
  - 新密码输入框
  - 确认新密码输入框
  - "重置密码"按钮
- "返回登录"链接

**交互逻辑：**
1. 输入邮箱并发送验证码
2. 收到验证码后显示步骤2
3. 提交重置请求
4. 成功后跳转登录页

## 7. 技术实现细节（Builder 决定）

### 7.1 后端技术选择

**必须使用（已确定）：**
- Spring Boot 3.2.2
- Spring Security（用于密码加密，不用于认证）
- Spring Mail（发送邮件）
- JWT 库：jjwt 0.12.x
- BCrypt（密码加密）

**Builder 决定：**
- 验证码存储：优先 Redis，如果项目没有则用数据库表 `email_verification_code`
- JWT 密钥：从配置文件读取，不硬编码
- 邮件模板：HTML 模板还是纯文本（建议 HTML）
- 异常处理：统一异常处理器
- 日志：记录关键操作（发送验证码、登录成功/失败）

### 7.2 前端技术选择

**必须使用（已确定）：**
- Vue 3 Composition API
- Element Plus
- Vue Router
- Axios

**Builder 决定：**
- 状态管理：Pinia 还是 Composition API + provide/inject
- 表单校验：Element Plus 内置校验还是 VeeValidate
- Token 刷新策略：不刷新（固定7天）
- 路由守卫：检查 Token 有效性

### 7.3 安全考虑

**后端安全：**
- 密码 BCrypt 加密（强度10）
- 验证码随机生成，不可预测
- JWT 签名防篡改
- 防刷机制：1分钟1次验证码
- SMTP 授权码存在配置文件，不提交到 Git

**前端安全：**
- Token 存 localStorage（不存 cookie，防 CSRF）
- 密码输入框 type="password"
- HTTPS 传输（生产环境）

### 7.4 邮件模板

**Builder 决定具体样式，内容要求：**

注册验证码邮件：
```
标题：【数据平台】注册验证码
内容：
您好！

您正在注册数据平台账号，验证码为：

    123456

验证码10分钟内有效，请勿泄露给他人。

如非本人操作，请忽略此邮件。
```

登录验证码邮件：
```
标题：【数据平台】登录验证码
内容：
您好！

您正在登录数据平台，验证码为：

    123456

验证码10分钟内有效，请勿泄露给他人。

如非本人操作，请立即修改密码。
```

重置密码验证码邮件：
```
标题：【数据平台】重置密码验证码
内容：
您好！

您正在重置数据平台密码，验证码为：

    123456

验证码10分钟内有效，请勿泄露给他人。

如非本人操作，请忽略此邮件。
```

## 8. 测试要点

### 8.1 功能测试
- 注册流程端到端测试
- 登录流程（首次、7天内、7天后）端到端测试
- 忘记密码流程端到端测试
- 验证码发送成功率测试
- 验证码过期测试
- Token 过期测试

### 8.2 异常场景测试
- 邮箱格式错误
- 密码格式错误
- 验证码错误
- 验证码过期
- 重复注册
- 不存在的邮箱登录
- 密码错误
- Token 过期后访问接口
- 并发发送验证码（防刷测试）

### 8.3 性能测试
- 邮件发送延迟
- 验证码生成性能
- JWT 生成和验证性能

## 9. 部署配置

### 9.1 后端配置（application.yml）

```yaml
spring:
  mail:
    host: smtp.163.com
    port: 465
    username: 17766238993@163.com
    password: ZRYM339mZrztCZyA  # 生产环境用环境变量
    protocol: smtp
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true
            required: true
    default-encoding: UTF-8

  # Redis 配置（如果使用）
  redis:
    host: localhost
    port: 6379
    database: 0

# JWT 配置
jwt:
  secret: your-secret-key-min-256-bits  # 生产环境用环境变量
  expiration: 604800000  # 7天（毫秒）

# 验证码配置
verification:
  code:
    length: 6
    expire-minutes: 10
    send-interval-seconds: 60
```

### 9.2 前端配置

```javascript
// .env.development
VITE_API_BASE_URL=http://localhost:8089

// .env.production
VITE_API_BASE_URL=https://your-production-domain.com
```

## 10. 后续扩展（不在本次范围）

- 短信验证码登录
- 第三方登录（微信、QQ）
- 双因素认证（2FA）
- 登录日志审计
- 异常登录检测
- Token 刷新机制
- 记住我功能
- 单点登录（SSO）

---

**文档版本：** 1.0  
**创建日期：** 2026-10-23  
**最后更新：** 2026-10-23
