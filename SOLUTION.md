# 启动问题解决方案总结

## 当前状态

✅ 项目已重新编译成功
✅ 已生成JAR文件: `target/data-platform-1.0.0.jar`
✅ 已简化配置类，移除可能导致问题的Bean定义

## 问题原因

你遇到的 `IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType'` 错误，通常是由于：

1. Spring Boot 3.x 与某些Bean定义方式不兼容
2. MyBatis-Plus的MetaObjectHandler配置方式有问题
3. 配置类中的某些返回类型定义不正确

## 已执行的修复

### 1. 简化了 MyBatisPlusConfig.java

**修改前**: 包含自定义的MetaObjectHandler Bean
**修改后**: 只保留必要的分页和乐观锁插件

```java
@Configuration
public class MyBatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

### 2. 优化了 SecurityConfig.java

简化了CORS配置的初始化方式，避免潜在的类型推断问题。

### 3. 重新编译项目

```bash
mvn clean package -DskipTests
```

## 现在请按以下步骤操作

### 步骤1：确保数据库已准备好

在MySQL中执行：

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS data_platform 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- 切换到数据库
USE data_platform;

-- 导入表结构（在命令行中执行）
SOURCE D:/alert4/src/main/resources/sql/schema.sql;

-- 验证
SHOW TABLES;
```

应该看到7张表被创建。

### 步骤2：配置数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 你的MySQL密码  # ← 修改这里
```

### 步骤3：重新启动应用

**方式A - 使用IDEA**:
1. 在IDEA中找到 `DataPlatformApplication.java`
2. 右键 -> Run 'DataPlatformApplication'
3. 查看控制台输出

**方式B - 使用命令行**:
```bash
cd D:\alert4
java -jar target\data-platform-1.0.0.jar
```

**方式C - 使用Maven**:
```bash
cd D:\alert4
mvn spring-boot:run
```

### 步骤4：观察启动日志

**成功的标志**:
```
Started DataPlatformApplication in X.XXX seconds (JVM running for X.XXX)
```

**如果还有错误**:
- 复制完整的错误堆栈信息
- 检查是否是数据库连接错误
- 检查是否是端口占用（8080）

## 如果仍然失败

### 尝试方案A：使用H2内存数据库测试

创建 `application-h2.yml`:

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb
    username: sa
    password: 
  h2:
    console:
      enabled: true
  jpa:
    hibernate:
      ddl-auto: create

jwt:
  secret: test-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256
  expiration: 86400000

encrypt:
  secret-key: 12345678901234567890123456789012
```

然后启动：
```bash
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=h2
```

如果H2可以启动，说明问题在于MySQL连接或配置。

### 尝试方案B：禁用Security

在 `SecurityConfig` 类上添加注释：
```java
// @Configuration
// @EnableWebSecurity
// @EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    // ...
}
```

重新编译并启动，看是否是Security配置的问题。

### 尝试方案C：降级Spring Boot版本

如果上述方案都不行，可能需要降级Spring Boot到3.1.x版本。

修改 `pom.xml`:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.1.5</version>  <!-- 从3.2.0改为3.1.5 -->
    <relativePath/>
</parent>
```

## 诊断工具

项目根目录下有两个诊断脚本：

1. **diagnose.bat** - 完整诊断工具
   - 检查Java/Maven版本
   - 检查编译状态
   - 尝试启动应用

2. **test-start.bat** - 使用测试配置启动
   - 使用 `application-test.yml` 配置
   - 适合调试环境

## 常见启动错误速查

| 错误信息 | 可能原因 | 解决方法 |
|---------|---------|---------|
| `Communications link failure` | MySQL未启动或连接信息错误 | 检查MySQL服务和连接配置 |
| `Table 'xxx' doesn't exist` | 数据库表未创建 | 执行schema.sql脚本 |
| `Access denied for user` | 用户权限不足 | 检查MySQL用户权限 |
| `Port 8080 already in use` | 端口被占用 | 修改端口或关闭占用进程 |
| `IllegalArgumentException: factoryBeanObjectType` | Bean定义问题 | 已修复，重新编译 |

## 获取详细日志

如果需要更详细的错误信息，启动时添加调试参数：

```bash
java -Ddebug=true \
     -Dlogging.level.root=DEBUG \
     -Dlogging.level.com.dataplatform=TRACE \
     -Dlogging.level.org.springframework=DEBUG \
     -jar target\data-platform-1.0.0.jar
```

## 成功后的测试

应用成功启动后，测试以下端点：

1. **健康检查**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
   应返回: `{"status":"UP"}`

2. **API文档**:
   浏览器访问: http://localhost:8080/swagger-ui.html

3. **登录接口**:
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
   ```
   应返回包含token的JSON

## 下一步计划

启动成功后，建议：

1. ✅ 测试所有API接口
2. ✅ 创建数据源
3. ✅ 创建作业
4. ✅ 配置SQL和字段元数据
5. ✅ 发布版本
6. ✅ 测试完整的ETL流程

## 项目文档索引

- `README.md` - 项目完整文档
- `QUICK_START.md` - 快速启动指南
- `TROUBLESHOOTING.md` - 详细故障排查
- `API.md` - API接口文档（如果需要可以创建）
- `SOLUTION.md` - 本文件，问题解决方案

## 联系支持

如果以上方案都无法解决问题，请提供：

1. 完整的控制台输出（从启动到错误）
2. `java -version` 输出
3. `mvn -version` 输出  
4. `application.yml` 配置（隐藏密码）
5. 数据库版本信息

祝启动成功！🚀
