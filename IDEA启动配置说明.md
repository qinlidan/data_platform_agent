# IDEA 启动配置说明

## 问题：JWT secret 长度不足

如果在 IDEA 中启动应用时报错：
```
JWT secret 必须至少 32 字符（256 位）
```

## 解决方案

### 方案 1：检查 IDEA 环境变量配置
1. 打开 IDEA，找到运行配置：`Run` -> `Edit Configurations...`
2. 选择 `DataPlatformApplication` 启动配置
3. 检查 `Environment variables` 是否设置了 `JWT_SECRET`
4. 如果设置了且长度不足 32 字符，请删除该环境变量或更新为：
   ```
   JWT_SECRET=dataplatform-jwt-secret-key-at-least-32-characters-for-hs256-algorithm
   ```

### 方案 2：使用配置文件的默认值
1. 确保 `application.yml` 中的默认值足够长（已更新为 71 字符）
2. 删除 IDEA 运行配置中的 `JWT_SECRET` 环境变量
3. 重新启动应用

### 方案 3：使用 Maven 启动（推荐）
在项目根目录执行：
```bash
mvn spring-boot:run
```

或在后台运行：
```bash
mvn spring-boot:run &
```

## 验证配置

启动后访问：
- 健康检查：http://localhost:8089/actuator/health
- Swagger UI：http://localhost:8089/swagger-ui.html
- API 文档：http://localhost:8089/v3/api-docs

## 当前配置

### application.yml
```yaml
jwt:
  secret: ${JWT_SECRET:dataplatform-jwt-secret-key-at-least-32-characters-for-hs256-algorithm}
  expiration: 604800000  # 7天
```

默认密钥长度：71 字符 ✅

### 测试环境 (application-test.yml)
```yaml
jwt:
  secret: test-secret-key-for-jwt-token-with-at-least-256-bits-length
  expiration: 86400000
```

测试密钥长度：59 字符 ✅

## 注意事项

1. **开发环境**：使用配置文件默认值即可
2. **生产环境**：必须通过环境变量 `JWT_SECRET` 设置强密钥
3. **密钥要求**：至少 32 字符（256 位），建议 64+ 字符
4. **安全性**：生产环境密钥不要提交到代码仓库
