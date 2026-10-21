# 🚨 紧急修复指南

## 当前情况

你的项目启动失败，错误信息：
```
java.lang.IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

## ✅ 我已经做的修复

1. **禁用了 SecurityConfig** - 注释掉了所有注解
2. **清空了 MyBatisPlusConfig** - 移除了所有可能有问题的Bean
3. **创建了最小化配置** - `application-minimal.yml`
4. **重新编译打包** - 生成了新的JAR文件

## 🎯 现在请按这个顺序测试

### 测试1：最小配置启动测试（最重要！）

**双击运行**：`test-minimal.bat`

或者在命令行执行：
```bash
cd D:\alert4
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

### 结果判断

#### ✅ 如果成功启动（看到 "Started DataPlatformApplication"）

说明问题在于被禁用的配置！按以下步骤恢复：

**步骤A - 恢复 MyBatisPlusConfig**：
```java
// 在 MyBatisPlusConfig.java 中添加：
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

重新测试：
```bash
mvn clean package -DskipTests
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

**步骤B - 如果A成功，恢复 SecurityConfig**：
```java
// 取消注释
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
```

删除 `application-minimal.yml` 中的：
```yaml
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

#### ❌ 如果仍然失败（同样的错误）

说明问题更深层！尝试以下方案：

**方案1：降级Spring Boot版本**

编辑 `pom.xml`，修改版本：
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.1.5</version>  <!-- 改这里 -->
    <relativePath/>
</parent>
```

然后：
```bash
mvn clean package -DskipTests
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

**方案2：降级MyBatis-Plus版本**

编辑 `pom.xml`：
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.3.1</version>  <!-- 改这里 -->
</dependency>
```

然后重新打包测试。

**方案3：查看完整的依赖树**

```bash
cd D:\alert4
mvn dependency:tree > dependencies.txt
```

把 `dependencies.txt` 文件内容发给我，我帮你分析。

## 📝 快速命令参考

**重新编译**：
```bash
cd D:\alert4
mvn clean package -DskipTests
```

**启动测试**：
```bash
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

**查看依赖**：
```bash
mvn dependency:tree
```

**检查Java版本**：
```bash
java -version
```

## 🔍 需要检查的配置文件

1. **数据库配置** - `application.yml`:
   ```yaml
   spring:
     datasource:
       username: root
       password: fra2781@  # ✅ 已确认
       url: jdbc:mysql://localhost:3306/data_platform...
   ```

2. **数据库是否已创建**：
   ```sql
   -- 在MySQL中执行
   CREATE DATABASE IF NOT EXISTS data_platform;
   USE data_platform;
   SOURCE D:/alert4/src/main/resources/sql/schema.sql;
   ```

## 💡 如果所有方法都失败

考虑使用Spring Boot 2.7.x（更稳定）：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
</parent>
```

但这需要调整一些代码（Security配置语法不同）。

## 📞 反馈信息

测试后，请告诉我：

1. ✅ `test-minimal.bat` 是否成功启动？
2. 📋 如果失败，完整的错误信息是什么？
3. ☕ Java版本：`java -version` 的输出
4. 🗄️ MySQL是否正在运行？版本是多少？

---

**立即行动**：双击运行 `test-minimal.bat`，然后告诉我结果！
