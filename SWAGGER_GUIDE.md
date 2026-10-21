# 📚 Swagger API 文档访问指南

## 🎯 Swagger 访问地址

项目已集成 **SpringDoc OpenAPI 2.3.0**，启动应用后可以通过以下地址访问：

### 1️⃣ Swagger UI 界面（推荐）
```
http://localhost:8080/swagger-ui.html
```
或
```
http://localhost:8080/swagger-ui/index.html
```

### 2️⃣ OpenAPI JSON 文档
```
http://localhost:8080/v3/api-docs
```

### 3️⃣ OpenAPI YAML 文档
```
http://localhost:8080/v3/api-docs.yaml
```

## 📋 完整的 API 接口列表

### 🔐 认证接口 (`AuthController`)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |

### 💾 数据源管理 (`DataSourceController`)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/datasources` | 创建数据源 |
| PUT | `/api/datasources/{id}` | 更新数据源 |
| DELETE | `/api/datasources/{id}` | 删除数据源 |
| GET | `/api/datasources/{id}` | 获取数据源详情 |
| GET | `/api/datasources/list` | 分页查询数据源 |
| GET | `/api/datasources/all` | 获取所有数据源 |
| POST | `/api/datasources/{id}/test` | 测试数据源连接 |

### 📊 作业管理 (`JobController`)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/jobs` | 创建作业 |
| PUT | `/api/jobs/{id}` | 更新作业 |
| DELETE | `/api/jobs/{id}` | 删除作业 |
| GET | `/api/jobs/{id}` | 获取作业详情 |
| GET | `/api/jobs/list` | 分页查询作业 |
| POST | `/api/jobs/{id}/publish` | 发布作业 |
| POST | `/api/jobs/{id}/archive` | 归档作业 |

### 📝 作业版本管理 (`JobVersionController`)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/job-versions/{jobId}/configure-sql` | 配置作业 SQL |
| GET | `/api/job-versions/job/{jobId}` | 获取作业所有版本 |
| GET | `/api/job-versions/{id}` | 获取版本详情 |
| POST | `/api/job-versions/{id}/publish` | 发布版本 |
| DELETE | `/api/job-versions/{id}` | 删除版本 |

### 🗂️ 字段元数据管理 (`FieldMetadataController`)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/field-metadata/batch` | 批量创建字段元数据 |
| PUT | `/api/field-metadata/{id}` | 更新字段元数据 |
| DELETE | `/api/field-metadata/{id}` | 删除字段元数据 |
| GET | `/api/field-metadata/{id}` | 获取字段元数据详情 |
| GET | `/api/field-metadata/version/{versionId}` | 获取版本的所有字段 |
| POST | `/api/field-metadata/parse-sql` | 从 SQL 解析字段 |

## 🚀 如何使用 Swagger

### 步骤 1：启动应用
确保应用正在运行，看到：
```
Started DataPlatformApplication in X.XXX seconds
```

### 步骤 2：访问 Swagger UI
浏览器打开：
```
http://localhost:8080/swagger-ui.html
```

### 步骤 3：测试接口

#### 示例 1：查询数据源列表
1. 找到 `DataSourceController`
2. 点击 `GET /api/datasources/list`
3. 点击 **Try it out**
4. 输入参数：
   - `pageNum`: 1
   - `pageSize`: 10
5. 点击 **Execute**
6. 查看响应结果

#### 示例 2：创建数据源
1. 找到 `POST /api/datasources`
2. 点击 **Try it out**
3. 输入 JSON：
```json
{
  "sourceCode": "test_db",
  "sourceName": "测试数据库",
  "sourceType": "MySQL",
  "jdbcUrl": "jdbc:mysql://localhost:3306/test",
  "username": "root",
  "password": "password"
}
```
4. 点击 **Execute**

## ⚠️ 注意事项

### 1. 当前配置：所有接口无需认证
由于已经关闭了认证，所有接口都可以直接测试，无需提供 Token。

### 2. 如果需要认证（未来）
如果恢复认证功能，使用步骤：
1. 先调用 `POST /api/auth/login` 获取 Token
2. 点击页面右上角的 **Authorize** 按钮
3. 输入：`Bearer <你的token>`
4. 点击 **Authorize**
5. 然后就可以测试需要认证的接口了

## 📦 Swagger 配置信息

当前配置（在 `application.yml` 中）：
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

## 🎨 Swagger UI 界面说明

访问 Swagger UI 后，你会看到：

1. **顶部导航栏** - 显示 API 标题和版本
2. **控制器列表** - 按控制器分组的接口
3. **每个接口卡片** - 显示：
   - HTTP 方法（GET/POST/PUT/DELETE）
   - 接口路径
   - 接口描述
   - 参数列表
   - 响应示例
4. **Try it out 按钮** - 点击后可以直接测试接口
5. **Execute 按钮** - 执行接口调用
6. **响应区域** - 显示返回的数据

## 📝 快速测试清单

测试以下接口确认 Swagger 正常工作：

- [ ] `GET /actuator/health` - 健康检查
- [ ] `GET /api/datasources/list` - 查询数据源
- [ ] `GET /api/jobs/list` - 查询作业
- [ ] `POST /api/datasources` - 创建数据源（可选）

## 🎉 开始使用

**现在就访问：**
```
http://localhost:8080/swagger-ui.html
```

你会看到一个完整的交互式 API 文档！🚀
