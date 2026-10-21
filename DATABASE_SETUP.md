# 数据库配置指南

## 1. 数据库初始化

### 步骤1：创建数据库
执行SQL脚本创建数据库和表：

```bash
# 使用MySQL命令行
mysql -u root -p < src/main/resources/sql/schema.sql

# 或者使用MySQL客户端工具直接执行 schema.sql 文件
```

脚本会自动创建：
- 数据库：`data_platform`
- 7张核心表
- 默认管理员账号

### 步骤2：配置数据库连接

编辑 `src/main/resources/application.yml` 文件，修改数据库连接信息：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root        # 修改为你的MySQL用户名
    password: root        # 修改为你的MySQL密码
```

### 步骤3：修改JWT密钥（可选）

建议修改JWT密钥以提高安全性：

```yaml
jwt:
  secret: your-custom-secret-key-here-at-least-256-bits-long  # 修改为你自己的密钥
  expiration: 86400000  # Token有效期：24小时（单位：毫秒）
```

## 2. 数据库表说明

### 核心表结构

| 表名 | 说明 | 主要字段 |
|-----|------|---------|
| `user` | 用户表 | username, password, nickname, email |
| `data_source` | 数据源表 | name, type, host, port, database_name |
| `job` | 任务表 | name, source_id, target_id, sql_content, status |
| `job_version` | 任务版本表 | job_id, version, sql_content, is_published |
| `table_metadata` | 表元数据表 | data_source_id, table_name, schema_name |
| `field_metadata` | 字段元数据表 | table_id, field_name, field_type |
| `field_lineage` | 字段血缘关系表 | source_field_id, target_field_id, transformation |

### 默认管理员账号

初始化脚本会创建一个默认管理员账号：
- **用户名**: `admin`
- **密码**: `admin123`
- **邮箱**: `admin@example.com`

⚠️ **重要提示**：首次登录后请立即修改默认密码！

## 3. 快速配置示例

### 场景1：本地开发环境

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

### 场景2：Docker MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql-container:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

### 场景3：远程数据库

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.1.100:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

## 4. 验证配置

### 启动应用
```bash
mvn spring-boot:run
```

### 检查日志
启动成功后，应该看到：
```
Started DataPlatformApplication in X.XXX seconds
```

### 测试登录接口
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

成功响应示例：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "username": "admin",
    "nickname": "管理员"
  }
}
```

## 5. 常见问题

### Q1: 连接数据库失败
**错误**: `Communications link failure`

**解决**:
1. 检查MySQL服务是否启动
2. 确认host和port配置正确
3. 检查防火墙设置
4. 验证用户名和密码

### Q2: 时区问题
**错误**: `The server time zone value 'XXX' is unrecognized`

**解决**: 在JDBC URL中添加 `serverTimezone=Asia/Shanghai`

### Q3: SSL警告
**警告**: `SSL connection warning`

**解决**: 
- 开发环境：添加 `useSSL=false`
- 生产环境：配置SSL证书或使用 `useSSL=true`

### Q4: 中文乱码
**解决**: 确保配置了正确的字符集
- JDBC URL: `characterEncoding=utf8`
- 数据库: `utf8mb4_unicode_ci`

## 6. 数据库维护

### 备份数据库
```bash
mysqldump -u root -p data_platform > backup_$(date +%Y%m%d).sql
```

### 恢复数据库
```bash
mysql -u root -p data_platform < backup_20261013.sql
```

### 清理测试数据
```sql
-- 保留管理员用户，清理其他数据
DELETE FROM field_lineage WHERE id > 0;
DELETE FROM field_metadata WHERE id > 0;
DELETE FROM table_metadata WHERE id > 0;
DELETE FROM job_version WHERE id > 0;
DELETE FROM job WHERE id > 0;
DELETE FROM data_source WHERE id > 0;
DELETE FROM user WHERE username != 'admin';
```

## 7. 下一步

配置完成后，可以：
1. 访问Swagger文档：http://localhost:8080/swagger-ui.html
2. 使用默认管理员账号登录
3. 创建数据源
4. 创建任务
5. 查看字段血缘关系

## 8. 安全建议

1. ✅ 修改默认管理员密码
2. ✅ 使用强密码策略
3. ✅ 定期更新JWT密钥
4. ✅ 生产环境启用SSL
5. ✅ 限制数据库访问权限
6. ✅ 定期备份数据库
