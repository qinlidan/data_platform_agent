# 自然语言作业管理接口使用指南

## 📋 目录
- [简介](#简介)
- [快速开始](#快速开始)
- [API接口详解](#api接口详解)
- [使用示例](#使用示例)
- [Swagger文档](#swagger文档)
- [测试方法](#测试方法)

---

## 简介

本系统提供了基于自然语言的作业管理接口，允许用户通过自然语言描述来创建、修改、发布和删除数据同步作业。系统集成了 Spring AI，支持中英文输入。

### 核心功能
- 🤖 自然语言理解与处理
- 📝 智能作业创建
- ✏️ 灵活的作业修改
- 🚀 一键作业发布
- 🗑️ 安全的作业删除
- 💬 通用对话交互

---

## 快速开始

### 1. 环境要求
- Java 17+
- Spring Boot 2.7.x
- MySQL 5.7+
- DeepSeek API Key（配置在环境变量 `DEEPSEEK_API_KEY` 中）

### 2. 启动应用
```bash
# 方式1: 使用Maven
mvn spring-boot:run

# 方式2: 直接运行JAR包
java -jar target/data-platform-1.0.0.jar
```

### 3. 访问地址
- 应用端口: `http://localhost:8080`
- Swagger文档: `http://localhost:8080/swagger-ui/index.html`
- API文档: `http://localhost:8080/v3/api-docs`

---

## API接口详解

### 基础路径
```
http://localhost:8080/api/nl
```

### 1. 通用对话接口

**接口**: `POST /api/nl/chat`

**功能**: 与AI助手进行通用对话，咨询数据平台功能、使用方法等。

**请求体**:
```json
{
  "userInput": "你好，请介绍一下数据平台的功能"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "response": "您好！我是数据平台的AI助手...",
    "timestamp": "2026-10-14T10:30:00"
  }
}
```

---

### 2. 创建作业接口

**接口**: `POST /api/nl/job/create`

**功能**: 通过自然语言描述创建新的数据同步作业。

**请求示例**:
```json
{
  "userInput": "帮我创建一个从MySQL的user表同步数据到Hive的ods_user表的作业"
}
```

**支持的描述要素**:
- 源数据源类型（MySQL、Oracle、PostgreSQL等）
- 源表名称
- 目标数据源（通常是Hive）
- 目标表名称
- 同步频率（可选）
- 数据过滤条件（可选）

**响应**:
```json
{
  "code": 200,
  "message": "作业创建成功",
  "data": {
    "jobId": 123,
    "jobCode": "JOB_20261014_001",
    "message": "已成功创建作业..."
  }
}
```

---

### 3. 修改作业接口

**接口**: `POST /api/nl/job/update`

**功能**: 通过自然语言描述修改现有作业的配置。

**请求示例**:
```json
{
  "userInput": "将作业JOB_20261014_001的调度时间改为每天凌晨2点执行"
}
```

**支持的修改内容**:
- 调度时间
- SQL查询语句
- 目标表名称
- Spark配置参数
- 作业描述

**响应**:
```json
{
  "code": 200,
  "message": "作业修改成功",
  "data": {
    "jobId": 123,
    "updateFields": ["调度时间"],
    "message": "作业配置已更新"
  }
}
```

---

### 4. 发布作业接口

**接口**: `POST /api/nl/job/publish`

**功能**: 发布作业，使其可以被调度执行。

**请求示例**:
```json
{
  "userInput": "发布作业JOB_20261014_001"
}
```

**发布前检查**:
- ✓ SQL配置完整
- ✓ 字段元数据已配置
- ✓ 作业状态允许发布

**响应**:
```json
{
  "code": 200,
  "message": "作业发布成功",
  "data": {
    "jobId": 123,
    "version": 1,
    "message": "作业已发布，可以开始调度执行"
  }
}
```

---

### 5. 删除作业接口

**接口**: `POST /api/nl/job/delete`

**功能**: 删除指定的作业（草稿状态或已归档的作业）。

**请求示例**:
```json
{
  "userInput": "删除作业JOB_20261014_001"
}
```

**删除限制**:
- ❌ 已发布的作业不能直接删除（需先归档）
- ✓ 草稿状态的作业可以删除
- ✓ 已归档的作业可以删除

**响应**:
```json
{
  "code": 200,
  "message": "作业删除成功",
  "data": {
    "jobId": 123,
    "message": "作业及相关配置已删除"
  }
}
```

---

## 使用示例

### 场景1: 完整的作业创建流程

```bash
# 1. 创建作业
curl -X POST http://localhost:8080/api/nl/job/create \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "userInput": "创建一个从MySQL的orders表同步到Hive的ods_orders表的作业，每天凌晨1点执行"
  }'

# 2. 配置SQL（如果需要自定义查询）
curl -X POST http://localhost:8080/api/nl/job/update \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "userInput": "修改作业JOB001的SQL，只同步最近7天的订单数据"
  }'

# 3. 发布作业
curl -X POST http://localhost:8080/api/nl/job/publish \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "userInput": "发布作业JOB001"
  }'
```

### 场景2: 咨询系统功能

```bash
curl -X POST http://localhost:8080/api/nl/chat \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "userInput": "如何查看作业的执行状态？"
  }'
```

### 场景3: 批量操作咨询

```bash
curl -X POST http://localhost:8080/api/nl/chat \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "userInput": "我有100个表需要同步，如何批量创建作业？"
  }'
```

---

## Swagger文档

### 访问Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

### Swagger功能
- 📖 完整的API文档
- 🧪 在线测试接口
- 📝 请求/响应示例
- 🔍 接口搜索

### 使用Swagger测试

1. 打开Swagger UI
2. 找到"自然语言作业管理"标签
3. 展开要测试的接口
4. 点击"Try it out"
5. 输入请求参数
6. 点击"Execute"查看结果

---

## 测试方法

### 使用curl测试（推荐）

```bash
# 测试聊天接口
curl -X POST http://localhost:8080/api/nl/chat \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "Hello"}'

# 测试创建作业
curl -X POST http://localhost:8080/api/nl/job/create \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "创建MySQL到Hive同步作业"}'
```

### 使用Postman测试

1. 创建新请求
2. 设置方法为 `POST`
3. 设置URL: `http://localhost:8080/api/nl/chat`
4. 设置Header: `Content-Type: application/json; charset=UTF-8`
5. 设置Body (raw JSON):
```json
{
  "userInput": "你好"
}
```
6. 点击Send发送请求

### 使用Python测试

```python
import requests
import json

url = "http://localhost:8080/api/nl/chat"
headers = {
    "Content-Type": "application/json; charset=UTF-8"
}
data = {
    "userInput": "帮我创建一个作业"
}

response = requests.post(url, headers=headers, json=data)
print(json.dumps(response.json(), ensure_ascii=False, indent=2))
```

---

## 常见问题

### Q1: 中文乱码怎么办？
A: 确保请求头包含 `charset=UTF-8`，并使用 `--data-binary` 或正确编码的JSON。

### Q2: 如何查看作业详情？
A: 使用传统的REST API: `GET /api/jobs/{id}`

### Q3: API返回500错误？
A: 检查DeepSeek API Key是否正确配置在环境变量中。

### Q4: 自然语言识别不准确？
A: 尽量使用清晰、具体的描述，包含关键信息如表名、数据源类型等。

---

## 技术架构

### 核心组件
- **NaturalLanguageController**: REST接口层
- **NaturalLanguageJobService**: 业务逻辑层
- **Spring AI**: 自然语言处理
- **DeepSeek API**: AI模型服务

### 数据流程
```
用户输入 → Controller → Service → Spring AI → DeepSeek → 
响应解析 → 业务处理 → 返回结果
```

---

## 相关文档

- [快速开始](./QUICK_START.md)
- [API文档](./docs/AI_README.md)
- [架构设计](./docs/architecture-design.md)
- [测试指南](./AI_TESTING_GUIDE.md)

---

## 更新日志

### v1.0.0 (2026-10-14)
- ✅ 实现5个核心接口
- ✅ 集成Spring AI和DeepSeek
- ✅ 支持中英文自然语言输入
- ✅ 完善的Swagger文档
- ✅ 统一的异常处理

---

## 联系支持

如有问题或建议，请联系开发团队。

**最后更新**: 2026-10-14
