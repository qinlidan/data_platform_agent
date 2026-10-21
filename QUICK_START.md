# 快速启动指南

## 问题分析

根据你提供的错误截图，应用启动时遇到了 `IllegalArgumentException` 错误：
```
Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

这通常是Spring框架在初始化Bean时发生的类型不匹配问题。

## 已完成的修复

我已经做了以下修改来解决这个问题：

1. **简化了 MyBatisPlusConfig**
   - 移除了可能导致问题的 `MetaObjectHandler` Bean定义
   - 只保留了必要的分页和乐观锁插件

2. **优化了 SecurityConfig**
   - 简化了CORS配置
   - 移除了可能引起问题的集合初始化方式

3. **重新编译了项目**
   - 执行了 `mvn clean package`
   - 生成了新的JAR文件

## 启动步骤

### 方式1：使用IDEA启动（推荐）

1. **在IDEA中打开项目**
   - File -> Open -> 选择 `D:\alert4` 目录

2. **等待Maven依赖加载完成**

3. **配置数据库连接**
   - 打开 `src/main/resources/application.yml`
   - 修改数据库连接信息：
     ```yaml
     spring:
       datasource:
         url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
         username: root
         password: 你的MySQL密码
     ```

4. **启动应用**
   - 找到 `src/main/java/com/dataplatform/DataPlatformApplication.java`
   - 右键 -> Run 'DataPlatformApplication'

### 方式2：使用命令行启动

```bash
# 进入项目目录
cd D:\alert4

# 启动应用
java -jar target\data-platform-1.0.0.jar
```

### 方式3：使用诊断脚本

双击运行项目根目录下的 `diagnose.bat` 脚本，它会：
- 检查Java和Maven版本
- 检查编译状态
- 尝试启动应用
- 显示详细的错误信息（如果有）

## 如果还是无法启动

### 步骤1：确认数据库已准备好

```sql
-- 1. 登录MySQL
mysql -u root -p

-- 2. 创建数据库
CREATE DATABASE IF NOT EXISTS data_platform 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- 3. 使用数据库
USE data_platform;

-- 4. 导入表结构
SOURCE D:/alert4/src/main/resources/sql/schema.sql;

-- 5. 验证表是否创建成功
SHOW TABLES;

-- 6. 查看用户表
SELECT * FROM user;
```

### 步骤2：检查application.yml配置

确保以下配置正确：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root        # 你的MySQL用户名
    password: root        # 你的MySQL密码

jwt:
  secret: your-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm
  expiration: 86400000

encrypt:
  secret-key: 12345678901234567890123456789012  # 必须是32字符
```

### 步骤3：清理并重新编译

```bash
cd D:\alert4
mvn clean
mvn compile
mvn package -DskipTests
```

### 步骤4：使用测试配置启动

如果主配置有问题，可以使用测试配置：

```bash
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=test
```

### 步骤5：查看详细日志

在启动时添加调试参数：

```bash
java -Ddebug=true -Dlogging.level.org.springframework=DEBUG -jar target\data-platform-1.0.0.jar
```

这会输出非常详细的Spring启动日志，帮助定位问题。

## 启动成功的标志

当看到以下日志时，说明应用已成功启动：

```
2026-10-13 19:XX:XX [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port(s): 8080 (http)
2026-10-13 19:XX:XX [main] INFO  c.d.DataPlatformApplication - Started DataPlatformApplication in X.XXX seconds
```

然后可以：

1. **访问Swagger UI**: http://localhost:8080/swagger-ui.html
2. **测试健康检查**: http://localhost:8080/actuator/health
3. **测试登录接口**:
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
   ```

## 常见错误及解决方案

### 错误1：端口8080被占用

**解决方案1**: 修改application.yml中的端口
```yaml
server:
  port: 8081
```

**解决方案2**: 找到并关闭占用8080端口的进程
```bash
netstat -ano | findstr :8080
taskkill /PID <进程ID> /F
```

### 错误2：数据库连接失败

**可能原因**:
- MySQL服务未启动
- 用户名或密码错误
- 数据库不存在
- 端口号错误

**解决**: 检查MySQL服务状态，确认连接信息正确

### 错误3：Table doesn't exist

**解决**: 执行schema.sql脚本初始化数据库表

### 错误4：Access denied

**解决**: 
```sql
GRANT ALL PRIVILEGES ON data_platform.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

## 获取帮助

如果问题依然存在，请提供：

1. **完整的错误日志**（从启动到错误发生的全部输出）
2. **环境信息**:
   ```bash
   java -version
   mvn -version
   mysql --version
   ```
3. **配置文件内容**（application.yml，隐藏密码）
4. **数据库表列表**:
   ```sql
   USE data_platform;
   SHOW TABLES;
   ```

## 项目文件说明

- `diagnose.bat` - 诊断脚本，自动检查环境并启动应用
- `test-start.bat` - 使用测试配置启动应用
- `TROUBLESHOOTING.md` - 详细的故障排查文档
- `QUICK_START.md` - 本文件，快速启动指南

## 下一步

启动成功后，建议阅读：
- `README.md` - 完整的项目文档
- `API.md` - API接口文档
- 访问 http://localhost:8080/swagger-ui.html 查看在线API文档
