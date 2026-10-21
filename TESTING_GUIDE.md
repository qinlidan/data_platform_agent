# 快速测试指南

## 前置条件

在测试之前，请确保：
- ✅ MySQL数据库已启动
- ✅ 已执行 `schema.sql` 初始化数据库
- ✅ 已在 `application.yml` 中配置数据库连接
- ✅ 应用已成功启动（无报错）

## 测试步骤

### 1. 用户登录

**请求**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

**预期响应**:
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

**保存Token**: 将返回的 `token` 值保存，后续所有请求都需要使用。

---

### 2. 创建数据源

**请求**:
```bash
curl -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "sourceCode": "mysql_source_01",
    "sourceName": "测试MySQL源",
    "sourceType": "MYSQL",
    "host": "localhost",
    "port": 3306,
    "databaseName": "test_db",
    "username": "root",
    "password": "root123",
    "description": "测试用MySQL数据源"
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "sourceCode": "mysql_source_01",
    "sourceName": "测试MySQL源",
    "sourceType": "MYSQL"
  }
}
```

---

### 3. 查询数据源列表

**请求**:
```bash
curl -X GET "http://localhost:8080/api/datasources?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**预期响应**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 1,
        "sourceCode": "mysql_source_01",
        "sourceName": "测试MySQL源"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1
  }
}
```

---

### 4. 创建作业

**请求**:
```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "jobCode": "job_001",
    "jobName": "测试数据迁移任务",
    "jobType": "DB2HIVE",
    "sourceDatasourceId": 1,
    "sourceDatabase": "test_db",
    "sourceTable": "users",
    "targetDatabase": "hive_db",
    "targetTable": "users",
    "engineType": "SPARK",
    "description": "从MySQL迁移用户数据到Hive"
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "jobCode": "job_001",
    "jobName": "测试数据迁移任务",
    "status": "DRAFT",
    "currentVersion": 1
  }
}
```

---

### 5. 配置作业SQL

**请求**:
```bash
curl -X POST http://localhost:8080/api/jobs/1/versions/sql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "sqlContent": "SELECT id, username, email, create_time FROM users WHERE status = 1",
    "sparkConfig": "{\"spark.executor.memory\":\"2g\",\"spark.executor.cores\":\"2\"}",
    "changeLog": "初始SQL配置"
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "配置成功",
  "data": {
    "id": 1,
    "version": 1,
    "sqlContent": "SELECT id, username, email, create_time FROM users WHERE status = 1"
  }
}
```

---

### 6. 解析SQL字段

**请求**:
```bash
curl -X POST http://localhost:8080/api/field-metadata/parse \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "sql": "SELECT id, username, email, create_time FROM users WHERE status = 1"
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "解析成功",
  "data": ["id", "username", "email", "create_time"]
}
```

---

### 7. 批量保存字段元数据

**请求**:
```bash
curl -X POST http://localhost:8080/api/field-metadata/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "jobVersionId": 1,
    "fields": [
      {
        "fieldName": "id",
        "fieldType": "BIGINT",
        "fieldComment": "用户ID"
      },
      {
        "fieldName": "username",
        "fieldType": "STRING",
        "fieldComment": "用户名"
      },
      {
        "fieldName": "email",
        "fieldType": "STRING",
        "fieldComment": "邮箱"
      },
      {
        "fieldName": "create_time",
        "fieldType": "TIMESTAMP",
        "fieldComment": "创建时间"
      }
    ]
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "保存成功",
  "data": null
}
```

---

### 8. 查询字段元数据

**请求**:
```bash
curl -X GET "http://localhost:8080/api/field-metadata/version/1" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**预期响应**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "jobVersionId": 1,
      "fieldName": "id",
      "fieldType": "BIGINT",
      "fieldComment": "用户ID"
    },
    {
      "id": 2,
      "jobVersionId": 1,
      "fieldName": "username",
      "fieldType": "STRING",
      "fieldComment": "用户名"
    }
  ]
}
```

---

### 9. 发布作业版本

**请求**:
```bash
curl -X POST http://localhost:8080/api/jobs/1/versions/1/publish \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**预期响应**:
```json
{
  "code": 200,
  "message": "发布成功",
  "data": null
}
```

---

### 10. 查询作业详情

**请求**:
```bash
curl -X GET http://localhost:8080/api/jobs/1 \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**预期响应**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "jobCode": "job_001",
    "jobName": "测试数据迁移任务",
    "status": "PUBLISHED",
    "currentVersion": 1,
    "publishedVersion": 1
  }
}
```

---

## 使用Postman测试

如果你更喜欢使用Postman，可以：

1. 导入以下环境变量：
   - `base_url`: `http://localhost:8080`
   - `token`: 登录后获得的token

2. 在每个请求的Headers中添加：
   - `Authorization`: `Bearer {{token}}`
   - `Content-Type`: `application/json`

---

## 常见问题

### Q1: 401 Unauthorized
**原因**: Token未提供或已过期  
**解决**: 重新登录获取新的token

### Q2: 403 Forbidden
**原因**: 权限不足  
**解决**: 检查用户角色和权限配置

### Q3: 400 Bad Request
**原因**: 请求参数格式错误  
**解决**: 检查JSON格式和必填字段

### Q4: 500 Internal Server Error
**原因**: 服务器内部错误  
**解决**: 查看应用日志，检查数据库连接

---

## 访问Swagger UI

应用启动后，可以访问在线API文档：

```
http://localhost:8080/swagger-ui.html
```

在Swagger UI中可以：
- 查看所有API接口
- 在线测试接口
- 查看请求/响应示例
- 不需要手动编写curl命令

---

## 下一步

完成基础测试后，你可以：
1. 测试字段血缘关系功能
2. 测试作业版本管理功能
3. 测试数据源连接测试功能
4. 集成前端界面
5. 配置调度任务
