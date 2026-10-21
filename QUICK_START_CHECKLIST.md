# 🚀 快速启动检查清单

## ⚡ 启动前检查（30秒）

### 1️⃣ 检查 MySQL 是否运行
```bash
# Windows
net start | findstr MySQL
```

✅ 如果看到 MySQL 相关服务，说明已运行  
❌ 如果没有，需要启动 MySQL 服务

### 2️⃣ 检查数据库是否存在
```sql
-- 在 MySQL 客户端执行
SHOW DATABASES LIKE 'data_platform';
```

✅ 如果存在，继续  
❌ 如果不存在，需要创建数据库：
```sql
CREATE DATABASE data_platform DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3️⃣ 检查 JDK 版本（在 IDEA 中）
- 项目配置显示 JDK 22 ✅

## 🎯 启动步骤

### 第 1 步：在 IDEA 中打开项目
确认项目已打开，等待索引完成

### 第 2 步：找到主启动类
```
src/main/java/com/dataplatform/DataPlatformApplication.java
```

### 第 3 步：右键运行
右键 → Run 'DataPlatformApplication'

### 第 4 步：观察控制台输出

**正在启动...**
```
Starting DataPlatformApplication using Java 22
```

**启动成功！** 🎉
```
Started DataPlatformApplication in 5.123 seconds
```

**启动失败？** ❌
- 看下面的"常见错误"章节

## ✅ 启动成功的标志

1. 控制台显示：`Started DataPlatformApplication`
2. 没有红色错误信息
3. 可以访问：http://localhost:8080/actuator/health

## ❌ 常见错误及解决方案

### 错误 1：数据库连接失败
```
Communications link failure
```

**解决**：
1. 启动 MySQL 服务
2. 检查 `application.yml` 中的密码是否正确

### 错误 2：表不存在
```
Table 'data_platform.xxx' doesn't exist
```

**解决**：
1. 运行数据库初始化脚本
2. 或者先注释掉引用该表的代码

### 错误 3：端口被占用
```
Port 8080 was already in use
```

**解决**：
```bash
netstat -ano | findstr :8080
taskkill /PID <进程ID> /F
```

### 错误 4：Bean 冲突
```
The bean 'xxx' could not be registered
```

**解决**：
- 检查是否有重复的 @Bean 定义
- 已经清理过了，不应该再出现

## 🧪 启动后测试

### 1. 健康检查
浏览器访问：
```
http://localhost:8080/actuator/health
```

期望响应：
```json
{"status":"UP"}
```

### 2. API 文档
浏览器访问：
```
http://localhost:8080/swagger-ui.html
```

应该看到完整的 API 文档界面

### 3. 测试 API（可选）
使用 Postman 或 curl：
```bash
curl http://localhost:8080/actuator/health
```

## 📊 当前项目配置状态

✅ SecurityConfig - 已启用  
✅ MyBatisPlusConfig - 已启用  
✅ PasswordEncoder - 已配置  
✅ JWT 认证 - 已启用  
✅ CORS - 已配置  
✅ Swagger - 已启用  

## 💡 提示

- 第一次启动可能需要 10-20 秒（初始化缓存）
- 后续启动会快很多（5 秒左右）
- 如果遇到问题，查看完整的错误堆栈

## 🆘 如果还是启动失败

把 **完整的错误日志** 发给我，包括：
1. 红色的错误信息
2. 异常堆栈（Exception stack trace）
3. 启动前的最后几行日志

我会帮你快速定位问题！

---

**现在就去 IDEA 启动项目吧！** 🚀
