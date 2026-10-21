# 当前问题诊断和解决步骤

## 问题描述

启动应用时出现错误：
```
java.lang.IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

## 已执行的临时修复

为了隔离问题，我已经：

### 1. 禁用了 SecurityConfig
```java
//@Configuration
//@EnableWebSecurity  
//@EnableMethodSecurity
```

### 2. 清空了 MyBatisPlusConfig
只保留了 `@Configuration` 注解，移除了所有Bean定义

### 3. 创建了最小化配置文件
`application-minimal.yml` - 禁用了Security自动配置

## 测试步骤

### 步骤1：测试最小配置能否启动

运行 `test-minimal.bat` 或执行：
```bash
cd D:\alert4
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

**如果成功启动**：
- 说明问题出在 SecurityConfig 或 MyBatisPlusConfig
- 继续步骤2

**如果仍然失败**：
- 说明问题在其他地方（可能是依赖冲突）
- 跳转到"深度诊断"部分

### 步骤2：逐步恢复配置

#### 2.1 先恢复 MyBatisPlusConfig

在 `MyBatisPlusConfig.java` 中恢复：
```java
@Configuration
public class MyBatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

重新编译并测试：
```bash
mvn clean package -DskipTests
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal
```

- **如果成功**：继续恢复SecurityConfig
- **如果失败**：问题在MyBatisPlusConfig

#### 2.2 恢复 SecurityConfig

取消注释：
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
```

同时修改 `application-minimal.yml`，删除：
```yaml
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

重新编译测试。

## 深度诊断

如果最小配置也无法启动，问题可能在于：

### 可能原因1：Spring Boot版本与依赖不兼容

**解决方案**：降级Spring Boot版本

修改 `pom.xml`:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.1.5</version>  <!-- 从3.2.0改为3.1.5 -->
    <relativePath/>
</parent>
```

然后：
```bash
mvn clean package -DskipTests
java -jar target\data-platform-1.0.0.jar
```

### 可能原因2：某个依赖包有问题

**排查方法**：
```bash
mvn dependency:tree > dependencies.txt
```

查看 dependencies.txt，检查是否有冲突的依赖。

### 可能原因3：Lombok导致的问题

有时候Lombok的 `@RequiredArgsConstructor` 可能导致问题。

**临时测试**：在SecurityConfig中手动写构造函数：
```java
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    // 手动构造函数替代 @RequiredArgsConstructor
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                         JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }
    
    // ... 其他方法
}
```

### 可能原因4：MyBatis-Plus版本问题

**解决方案**：降级MyBatis-Plus

修改 `pom.xml`:
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.3.1</version>  <!-- 从3.5.5改为3.5.3.1 -->
</dependency>
```

## 启用详细日志

创建 `logback-spring.xml` 来获取更详细的错误信息：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="org.springframework" level="DEBUG"/>
    <logger name="org.springframework.beans" level="TRACE"/>
    <logger name="com.dataplatform" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

然后启动应用，会看到更详细的Bean创建过程。

## 最后的办法：使用Spring Boot 2.7.x

如果所有方法都失败，考虑降级到Spring Boot 2.7.x：

修改 `pom.xml`:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
    <relativePath/>
</parent>
```

同时需要调整：
- Spring Security配置语法（2.7.x与3.x语法不同）
- Java版本可以降到11或17
- 某些API可能需要调整

## 当前文件状态

- ✅ `MyBatisPlusConfig.java` - 已清空，仅保留@Configuration
- ✅ `SecurityConfig.java` - 已注释掉所有注解
- ✅ `application-minimal.yml` - 最小化配置，禁用Security
- ✅ `test-minimal.bat` - 测试启动脚本
- ✅ `target/data-platform-1.0.0.jar` - 已重新打包

## 下一步行动

1. **立即执行**：运行 `test-minimal.bat` 查看最小配置能否启动
2. **如果成功**：逐步恢复配置找出问题点
3. **如果失败**：尝试降级Spring Boot版本
4. **最后手段**：降级到Spring Boot 2.7.x

## 获取更多信息

如果需要我进一步协助，请提供：

1. 运行 `test-minimal.bat` 的完整输出
2. Java版本：`java -version`
3. Maven版本：`mvn -version`
4. MySQL版本
5. 是否能连接到数据库

祝你好运！🤞
