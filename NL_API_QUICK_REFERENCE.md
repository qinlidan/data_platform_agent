# 自然语言API快速参考

## 🚀 5个核心接口

### 1️⃣ 通用对话
```bash
POST /api/nl/chat
{"userInput": "你好，请介绍一下系统功能"}
```

### 2️⃣ 创建作业
```bash
POST /api/nl/job/create
{"userInput": "创建从MySQL的user表同步到Hive的ods_user表的作业"}
```

### 3️⃣ 修改作业
```bash
POST /api/nl/job/update
{"userInput": "修改作业JOB001的调度时间为每天凌晨2点"}
```

### 4️⃣ 发布作业
```bash
POST /api/nl/job/publish
{"userInput": "发布作业JOB001"}
```

### 5️⃣ 删除作业
```bash
POST /api/nl/job/delete
{"userInput": "删除作业JOB001"}
```

---

## 💡 快速测试命令

```bash
# 基础URL
BASE_URL="http://localhost:8080/api/nl"

# 1. 测试对话
curl -X POST $BASE_URL/chat \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "Hello, how can I create a job?"}'

# 2. 创建作业（中文）
echo '{"userInput": "帮我创建一个MySQL到Hive的同步作业"}' > /tmp/create.json
curl -X POST $BASE_URL/job/create \
  -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary @/tmp/create.json

# 3. 修改作业
echo '{"userInput": "修改作业的执行时间"}' > /tmp/update.json
curl -X POST $BASE_URL/job/update \
  -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary @/tmp/update.json

# 4. 发布作业
curl -X POST $BASE_URL/job/publish \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "publish the job"}'

# 5. 删除作业
curl -X POST $BASE_URL/job/delete \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"userInput": "delete job JOB001"}'
```

---

## 📊 响应格式

### 成功响应
```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "response": "AI处理结果...",
    "timestamp": "2026-10-14T10:30:00"
  }
}
```

### 错误响应
```json
{
  "code": 500,
  "message": "处理失败的原因",
  "data": null
}
```

---

## 🔑 关键配置

### 环境变量
```bash
export DEEPSEEK_API_KEY=your_api_key_here
```

### application.yml
```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
```

---

## 🎯 常用示例

### 英文示例
```bash
# 咨询功能
"What features does this platform provide?"

# 创建作业
"Create a data sync job from MySQL orders table to Hive"

# 修改作业
"Change the schedule of job JOB001 to run daily at 2 AM"

# 发布作业
"Publish job JOB001"

# 删除作业
"Delete job JOB001"
```

### 中文示例
```bash
# 咨询功能
"这个平台有什么功能？"

# 创建作业
"帮我创建一个从MySQL的orders表同步到Hive的作业"

# 修改作业
"把JOB001的调度时间改成每天凌晨2点"

# 发布作业
"发布作业JOB001"

# 删除作业
"删除作业JOB001"
```

---

## 🔗 快速链接

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **API Docs**: http://localhost:8080/v3/api-docs
- **Health Check**: http://localhost:8080/actuator/health

---

## ⚡ 一键测试脚本

```bash
#!/bin/bash
# 保存为 test_nl_api.sh

BASE="http://localhost:8080/api/nl"

echo "测试1: 对话接口"
curl -s -X POST $BASE/chat \
  -H "Content-Type: application/json" \
  -d '{"userInput":"Hello"}' | jq .

echo -e "\n测试2: 创建作业"
curl -s -X POST $BASE/job/create \
  -H "Content-Type: application/json" \
  -d '{"userInput":"create job"}' | jq .

echo -e "\n测试3: 修改作业"
curl -s -X POST $BASE/job/update \
  -H "Content-Type: application/json" \
  -d '{"userInput":"update job"}' | jq .

echo -e "\n测试4: 发布作业"
curl -s -X POST $BASE/job/publish \
  -H "Content-Type: application/json" \
  -d '{"userInput":"publish job"}' | jq .

echo -e "\n测试5: 删除作业"
curl -s -X POST $BASE/job/delete \
  -H "Content-Type: application/json" \
  -d '{"userInput":"delete job"}' | jq .

echo -e "\n✅ 所有测试完成"
```

---

## 📝 提示

1. ✅ 使用 `charset=UTF-8` 避免中文乱码
2. ✅ 中文输入建议使用 `--data-binary @file.json`
3. ✅ 测试前确保应用已启动
4. ✅ 检查 DeepSeek API Key 配置
5. ✅ 查看 Swagger 文档获取更多细节

---

**完整文档**: [NATURAL_LANGUAGE_API_GUIDE.md](./NATURAL_LANGUAGE_API_GUIDE.md)
