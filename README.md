# 数据开发平台 (Data Development Platform)

## 项目简介

基于 Spring Boot 3.x 的企业级数据开发平台，支持 DB2 到 Hive 的 ETL 作业管理、版本控制和元数据管理。

## 技术栈

- **后端框架**: Spring Boot 3.2.0
- **数据库**: MySQL 5.7+
- **ORM框架**: MyBatis-Plus 3.5.5
- **安全框架**: Spring Security 6.x + JWT
- **文档工具**: SpringDoc OpenAPI 3.x
- **构建工具**: Maven 3.8+
- **Java版本**: JDK 17+

## 功能特性

### 1. 用户认证
- JWT Token 认证
- 密码加密存储
- Token 过期管理

### 2. 数据源管理
- 支持多种数据源类型（MySQL、Hive、DB2等）
- 数据源连接测试
- 密码AES加密存储
- 数据源引用检查

### 3. 作业管理
- 作业创建、编辑、删除
- 作业状态管理（草稿、已发布、已归档）
- 支持 Hive2Hive、DB2Hive、Spark SQL 等作业类型
- 作业版本控制

### 4. 版本管理
- SQL 配置管理
- Spark 配置管理
- 版本发布和回滚
- 版本变更日志

### 5. 字段元数据管理
- 字段信息管理
- 数据类型管理
- 主键、分区键标识
- SQL 字段自动解析

### 6. 🆕 自然语言接口（AI驱动）
- **通用对话**: 与AI助手交流，咨询系统功能
- **智能创建**: 用自然语言描述即可创建作业
- **灵活修改**: 用口语化表达修改作业配置
- **一键发布**: 自然语言指令发布作业
- **安全删除**: 通过对话方式删除作业
- **中英双语**: 支持中文和英文输入
- **集成DeepSeek**: 基于Spring AI框架

## 项目结构

```
src/main/java/com/dataplatform/
├── common/              # 公共类
│   ├── ApiResponse.java
│   └── BaseEntity.java
├── config/              # 配置类
│   ├── MyBatisPlusConfig.java
│   └── SecurityConfig.java
├── controller/          # 控制器
│   ├── AuthController.java
│   ├── DataSourceController.java
│   ├── FieldMetadataController.java
│   ├── JobController.java
│   └── JobVersionController.java
├── dto/                 # 数据传输对象
│   ├── DataSourceRequest.java
│   ├── FieldMetadataRequest.java
│   ├── JobCreateRequest.java
│   ├── JobUpdateRequest.java
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   └── SqlConfigRequest.java
├── entity/              # 实体类
│   ├── DataSource.java
│   ├── FieldMetadata.java
│   ├── Job.java
│   ├── JobVersion.java
│   └── User.java
├── enums/               # 枚举类
│   ├── DataSourceType.java
│   ├── DataType.java
│   ├── EngineType.java
│   ├── JobStatus.java
│   └── JobType.java
├── exception/           # 异常处理
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── mapper/              # MyBatis Mapper
│   ├── DataSourceMapper.java
│   ├── FieldMetadataMapper.java
│   ├── JobMapper.java
│   ├── JobVersionMapper.java
│   └── UserMapper.java
├── security/            # 安全配置
│   ├── JwtAuthenticationEntryPoint.java
│   ├── JwtAuthenticationFilter.java
│   └── UserDetailsServiceImpl.java
├── service/             # 服务接口
│   ├── AuthService.java
│   ├── DataSourceService.java
│   ├── FieldMetadataService.java
│   ├── JobService.java
│   └── JobVersionService.java
├── service/impl/        # 服务实现
│   ├── AuthServiceImpl.java
│   ├── DataSourceServiceImpl.java
│   ├── FieldMetadataServiceImpl.java
│   ├── JobServiceImpl.java
│   └── JobVersionServiceImpl.java
├── util/                # 工具类
│   ├── EncryptUtil.java
│   ├── JwtUtil.java
│   └── SqlParserUtil.java
└── DataPlatformApplication.java
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 5.7+

### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE data_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行建表脚本
mysql -u root -p data_platform < src/main/resources/sql/schema.sql
```

### 3. 配置文件

编辑 `src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

修改加密密钥（建议使用环境变量）：

```yaml
encrypt:
  secret-key: your-32-character-secret-key!!
```

### 4. 配置DeepSeek API（自然语言功能）

```bash
# 设置环境变量
export DEEPSEEK_API_KEY=your_deepseek_api_key_here
```

或在 `application.yml` 中配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
```

### 5. 编译运行

```bash
# 编译
mvn clean package

# 运行
java -jar target/data-platform-1.0.0.jar

# 或使用 Maven 运行
mvn spring-boot:run
```

### 6. 访问应用

- 应用地址: http://localhost:8080
- Swagger文档: http://localhost:8080/swagger-ui/index.html
- 自然语言API: http://localhost:8080/api/nl/*

## 🤖 自然语言接口使用

### 快速测试

```bash
# 1. 通用对话
curl -X POST http://localhost:8080/api/nl/chat \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "你好，请介绍一下系统功能"}'

# 2. 创建作业
curl -X POST http://localhost:8080/api/nl/job/create \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "创建从MySQL的user表同步到Hive的ods_user表的作业"}'

# 3. 修改作业
curl -X POST http://localhost:8080/api/nl/job/update \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "修改作业的调度时间"}'

# 4. 发布作业
curl -X POST http://localhost:8080/api/nl/job/publish \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "发布作业JOB001"}'

# 5. 删除作业
curl -X POST http://localhost:8080/api/nl/job/delete \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "删除作业JOB001"}'
```

### 详细文档

- 📖 [自然语言API完整指南](./NATURAL_LANGUAGE_API_GUIDE.md) - 详细的API说明、使用示例、测试方法
- ⚡ [快速参考手册](./NL_API_QUICK_REFERENCE.md) - 常用命令和快速查询

## API 文档

### 认证接口

#### 登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "username": "admin",
    "nickname": "管理员"
  }
}
```

### 数据源接口

#### 创建数据源
```http
POST /api/datasources
Authorization: Bearer {token}
Content-Type: application/json

{
  "sourceCode": "mysql_prod",
  "sourceName": "生产MySQL",
  "sourceType": "MYSQL",
  "jdbcUrl": "jdbc:mysql://localhost:3306/test",
  "username": "root",
  "password": "password"
}
```

#### 测试连接
```http
POST /api/datasources/{id}/test
Authorization: Bearer {token}
```

### 作业接口

#### 创建作业
```http
POST /api/jobs
Authorization: Bearer {token}
Content-Type: application/json

{
  "jobCode": "etl_user_data",
  "jobName": "用户数据同步",
  "jobType": "DB2HIVE",
  "sourceDatasourceId": 1,
  "sourceDatabase": "source_db",
  "sourceTable": "users",
  "targetDatabase": "target_db",
  "targetTable": "users",
  "engineType": "SPARK",
  "description": "用户数据从DB2同步到Hive"
}
```

#### 配置SQL
```http
POST /api/job-versions/{jobId}/configure-sql
Authorization: Bearer {token}
Content-Type: application/json

{
  "sqlContent": "SELECT id, name, email FROM users WHERE create_time > '2024-01-01'",
  "sparkConfig": "{\"spark.executor.memory\":\"4g\"}",
  "changeLog": "添加时间过滤条件"
}
```

#### 发布版本
```http
POST /api/jobs/{id}/publish?version=1
Authorization: Bearer {token}
```

### 字段元数据接口

#### 批量保存字段
```http
POST /api/field-metadata/batch
Authorization: Bearer {token}
Content-Type: application/json

{
  "jobVersionId": 1,
  "fields": [
    {
      "fieldName": "id",
      "dataType": "BIGINT",
      "comment": "用户ID",
      "isPrimaryKey": true,
      "isNullable": false
    },
    {
      "fieldName": "name",
      "dataType": "STRING",
      "comment": "用户名",
      "isPrimaryKey": false,
      "isNullable": false
    }
  ]
}
```

#### 从SQL解析字段
```http
POST /api/field-metadata/parse
Authorization: Bearer {token}
Content-Type: application/json

"SELECT id, name, email FROM users"
```

## 配置说明

### JWT 配置

```yaml
jwt:
  secret: your-jwt-secret-key-must-be-at-least-32-characters-long
  expiration: 86400000  # 24小时（毫秒）
```

### 加密配置

```yaml
encrypt:
  secret-key: your-32-character-secret-key!!  # AES密钥，必须32字符
```

建议使用环境变量配置敏感信息：

```bash
export JWT_SECRET=your-jwt-secret
export ENCRYPT_SECRET_KEY=your-encrypt-key
export DB_PASSWORD=your-db-password
```

## 安全建议

1. **生产环境配置**
   - 修改默认的 JWT 密钥和加密密钥
   - 使用环境变量或密钥管理服务存储敏感信息
   - 启用 HTTPS

2. **密码策略**
   - 强制使用强密码
   - 定期更换密码
   - 实现密码错误次数限制

3. **审计日志**
   - 记录所有敏感操作
   - 定期审查日志
   - 实现日志告警

## 开发指南

### 添加新的数据源类型

1. 在 `DataSourceType` 枚举中添加新类型
2. 实现数据源连接测试逻辑
3. 更新 API 文档

### 添加新的作业类型

1. 在 `JobType` 枚举中添加新类型
2. 实现作业执行逻辑
3. 更新字段元数据校验规则

### 自定义 SQL 解析器

修改 `SqlParserUtil` 类，支持更复杂的 SQL 语法。

## 常见问题

### 1. JWT Token 过期如何处理？

前端需要实现 Token 刷新机制，或在 Token 过期时引导用户重新登录。

### 2. 如何支持更多数据库类型？

在 `DataSourceType` 枚举中添加新类型，并确保对应的 JDBC 驱动已添加到项目依赖中。

### 3. SQL 解析失败怎么办？

SQL 解析是辅助功能，解析失败时可以手动配置字段元数据。

## 后续优化

- [ ] 实现作业调度功能
- [ ] 添加作业执行监控
- [ ] 实现数据质量检查
- [ ] 添加血缘分析功能
- [ ] 实现作业依赖管理
- [ ] 完善审计日志
- [ ] 实现细粒度权限控制
- [ ] 添加通知告警功能

## 许可证

本项目采用 MIT 许可证。

## 联系方式

如有问题或建议，请提交 Issue 或 Pull Request。
