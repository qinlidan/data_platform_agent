# 🚀 快速启动指南

## 📋 升级到 Java 17 后的操作流程

### 第一步：下载并安装 Java 17

1. **下载 Java 17**：
   - 访问：https://adoptium.net/temurin/releases/?version=17
   - 下载 Windows x64 的 `.msi` 安装包

2. **安装**：
   - 运行下载的安装包
   - ✅ 勾选 "Add to PATH"
   - ✅ 勾选 "Set JAVA_HOME"
   - 完成安装

3. **重启命令行**：
   - 关闭所有命令行窗口
   - 重新打开新的命令行窗口

### 第二步：验证 Java 17 安装

**双击运行**：`check-java17.bat`

或在命令行执行：
```bash
cd D:\alert4
check-java17.bat
```

**期望看到**：
```
✅ Java 17 验证成功！
🎉 环境配置正确！可以开始编译项目
```

**如果失败**：
- 检查 `JAVA_HOME` 环境变量是否正确
- 确认 `java -version` 显示的是 17.x
- 参考 `JAVA17_UPGRADE_GUIDE.md` 的问题排查部分

### 第三步：重新编译项目

**双击运行**：`rebuild.bat`

或在命令行执行：
```bash
cd D:\alert4
mvn clean package -DskipTests
```

**期望看到**：
```
🎉 编译成功！
JAR 文件位置：target\data-platform-1.0.0.jar
```

### 第四步：启动应用

#### 选项A：使用正常配置启动

**双击运行**：`start-app.bat`

或在命令行执行：
```bash
cd D:\alert4
java -jar target\data-platform-1.0.0.jar
```

#### 选项B：使用最小配置测试（推荐首次测试）

**双击运行**：`test-minimal.bat`

或在命令行执行：
```bash
cd D:\alert4
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

### 第五步：验证应用启动成功

**成功标志**：
```
Started DataPlatformApplication in X.XXX seconds
```

**访问应用**：
- 健康检查：http://localhost:8080/actuator/health
- API文档：http://localhost:8080/swagger-ui.html

## 📂 项目文件说明

### 脚本文件
- `check-java17.bat` - 验证 Java 17 安装
- `rebuild.bat` - 重新编译项目
- `start-app.bat` - 启动应用（正常配置）
- `test-minimal.bat` - 启动应用（最小配置）
- `diagnose.bat` - 完整诊断工具

### 文档文件
- `JAVA17_UPGRADE_GUIDE.md` - Java 17 详细升级指南
- `QUICK_FIX.md` - 快速修复指南
- `DIAGNOSIS.md` - 问题诊断文档
- `SOLUTION.md` - 完整解决方案
- `README.md` - 项目文档

### 配置文件
- `application.yml` - 主配置文件
- `application-minimal.yml` - 最小化配置（用于测试）
- `application-test.yml` - 测试配置

## 🔧 如果启动仍然失败

### 情况1：最小配置可以启动，正常配置失败

说明问题在于 Security 或 MyBatis-Plus 配置。

**解决方法**：逐步恢复配置

1. **恢复 MyBatisPlusConfig**：
   
编辑 `src/main/java/com/dataplatform/config/MyBatisPlusConfig.java`：
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

重新编译测试：`rebuild.bat`

2. **如果成功，恢复 SecurityConfig**：

编辑 `src/main/java/com/dataplatform/config/SecurityConfig.java`：
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    // ... 保持其他代码不变
}
```

删除 `application-minimal.yml` 中的 Security 排除配置。

重新编译测试。

### 情况2：所有配置都失败

**检查数据库**：

1. 确认 MySQL 正在运行
2. 确认数据库 `data_platform` 已创建
3. 确认表结构已导入

```sql
-- 在 MySQL 命令行中执行
CREATE DATABASE IF NOT EXISTS data_platform;
USE data_platform;
SOURCE D:/alert4/src/main/resources/sql/schema.sql;
SHOW TABLES;
```

应该看到 7 张表。

**检查连接配置**：

编辑 `application.yml`，确认：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: fra2781@
```

## 🎯 完整的启动流程总结

```
1. 安装 Java 17 → 重启命令行
   ↓
2. 运行 check-java17.bat → 验证安装
   ↓
3. 运行 rebuild.bat → 重新编译
   ↓
4. 运行 test-minimal.bat → 测试启动
   ↓
5. 如果成功 → 恢复配置 → 运行 start-app.bat
   如果失败 → 检查数据库和配置
```

## 📞 获取帮助

如果按照以上步骤仍无法解决，请提供：

1. `check-java17.bat` 的完整输出
2. `rebuild.bat` 的完整输出
3. 启动时的完整错误日志
4. MySQL 版本和运行状态
5. `application.yml` 配置内容（隐藏密码）

---

**现在开始操作吧！** 

第一步：下载并安装 Java 17
- 下载地址：https://adoptium.net/temurin/releases/?version=17

安装完成后运行 `check-java17.bat` 验证安装！
