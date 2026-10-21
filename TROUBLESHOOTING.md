# 启动故障排查指南

## 当前错误分析

根据你的截图，错误信息是：
```
java.lang.IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

这个错误通常由以下原因引起：

## 解决方案

### 方案1：检查Spring Boot和依赖版本兼容性

已经简化了 `MyBatisPlusConfig.java`，移除了可能导致问题的 `MetaObjectHandler` Bean定义。

### 方案2：清理并重新编译

```bash
# 清理Maven缓存
mvn clean

# 重新编译
mvn compile

# 重新启动
mvn spring-boot:run
```

### 方案3：检查是否有其他配置类冲突

运行以下命令检查所有Bean定义：
```bash
grep -r "@Bean" src/main/java --include="*.java"
```

当前项目中的Bean定义应该只有：
- `MyBatisPlusConfig.mybatisPlusInterceptor()`
- `SecurityConfig` 中的4个Bean

### 方案4：临时禁用Security配置测试

如果上述方案都不行，可以尝试临时注释掉 SecurityConfig 来确定问题所在。

## 快速测试步骤

### 1. 确保数据库已创建并初始化

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE IF NOT EXISTS data_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 选择数据库
USE data_platform;

# 导入建表脚本
SOURCE D:/alert4/src/main/resources/sql/schema.sql;

# 验证表是否创建成功
SHOW TABLES;

# 应该看到7张表：
# - user
# - data_source
# - etl_job
# - etl_job_version
# - etl_table_metadata
# - etl_field_metadata
# - etl_field_lineage

# 检查是否有默认用户
SELECT * FROM user;
```

### 2. 检查application.yml配置

编辑 `src/main/resources/application.yml`，确保数据库连接信息正确：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # 修改为你的MySQL用户名
    password: your_password # 修改为你的MySQL密码
```

### 3. 使用Maven命令启动（推荐）

```bash
cd D:\alert4
mvn clean spring-boot:run
```

### 4. 使用IDEA启动

在IDEA中：
1. 右键点击 `DataPlatformApplication.java`
2. 选择 "Run 'DataPlatformApplication'"
3. 查看控制台输出

## 常见启动错误及解决方法

### 错误1：Communications link failure

**原因**: 无法连接到MySQL数据库

**解决**:
1. 检查MySQL服务是否启动
2. 检查端口3306是否正确
3. 检查用户名密码是否正确
4. 检查防火墙设置

### 错误2：Table 'data_platform.user' doesn't exist

**原因**: 数据库表未创建

**解决**: 执行 `schema.sql` 初始化脚本

### 错误3：Access denied for user 'root'@'localhost'

**原因**: MySQL用户权限问题

**解决**:
```sql
GRANT ALL PRIVILEGES ON data_platform.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

### 错误4：IllegalArgumentException (当前错误)

**原因**: Bean定义冲突或版本不兼容

**解决**: 
1. 已简化配置类，移除可能的问题Bean
2. 清理重新编译：`mvn clean compile`
3. 如果还不行，尝试降级Spring Boot版本到3.1.x

## 测试应用是否启动成功

### 方法1：查看控制台日志

成功启动后应该看到：
```
Started DataPlatformApplication in X.XXX seconds
```

### 方法2：访问健康检查端点

```bash
curl http://localhost:8080/actuator/health
```

应该返回：
```json
{"status":"UP"}
```

### 方法3：访问Swagger UI

打开浏览器访问：
```
http://localhost:8080/swagger-ui.html
```

应该看到API文档页面。

### 方法4：测试登录接口

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

应该返回包含token的JSON响应。

## 如果以上都不行

### 尝试最小化配置

1. 创建一个最简单的Controller测试：

```java
@RestController
@RequestMapping("/test")
public class TestController {
    
    @GetMapping("/hello")
    public String hello() {
        return "Hello World!";
    }
}
```

2. 临时注释掉 `@EnableWebSecurity` 注解

3. 逐步添加配置，找出问题所在

## 获取更详细的错误信息

在IDEA中启动时，在VM options中添加：
```
-Ddebug=true
-Dlogging.level.org.springframework=DEBUG
```

这会输出更详细的Spring启动日志，有助于定位问题。

## 联系支持

如果问题依然存在，请提供以下信息：
1. 完整的错误堆栈信息
2. `mvn -version` 的输出
3. `java -version` 的输出
4. `application.yml` 配置（隐藏敏感信息）
5. MySQL版本信息
