# 🚀 AI 功能 - 5分钟快速启动指南

## ⚡ 超快速启动（3步）

### 第1步：设置 API Keys (1分钟)

**在 IDEA 中设置环境变量：**

1. 点击右上角的运行配置下拉菜单
2. 选择 **Edit Configurations...**
3. 找到 `DataPlatformApplication`
4. 在 **Environment variables** 框中点击文件夹图标
5. 添加以下两个变量：

```
DEEPSEEK_API_KEY=sk-your-deepseek-key-here
ALI_AI_KEY=sk-your-ali-key-here
```

6. 点击 **OK** 保存

> **💡 提示：** 暂时没有真实的 API Key？使用默认值也能启动，只是调用 AI 会失败。

---

### 第2步：启动应用 (1分钟)

在 IDEA 中：
1. 打开 `DataPlatformApplication.java`
2. 点击绿色运行按钮 ▶️
3. 等待看到：`Started DataPlatformApplication in X.XXX seconds`

---

### 第3步：测试验证 (1分钟)

**方式1：浏览器访问**
```
http://localhost:8080/api/ai/health
```

看到 `"status": "UP"` 就成功了！

**方式2：Swagger UI**
```
http://localhost:8080/swagger-ui.html
```

找到 **AI助手** 分组，点击任意接口测试！

---

## 🎯 立即体验 AI 功能

### 体验1：AI 对话 (30秒)

在 Swagger UI 中：
1. 找到 `POST /api/ai/chat`
2. 点击 **Try it out**
3. 输入：
```json
{
  "message": "你好，请介绍一下你的功能"
}
```
4. 点击 **Execute**
5. 查看 AI 的回复！

---

### 体验2：智能生成 SQL (30秒)

1. 找到 `POST /api/ai/generate-sql`
2. 点击 **Try it out**
3. 输入：
```json
{
  "requirement": "查询最近7天的订单，按金额降序",
  "databaseType": "MySQL",
  "tableName": "orders"
}
```
4. 点击 **Execute**
5. 获得完整的 SQL 语句！

---

### 体验3：提取作业配置 (30秒)

1. 找到 `POST /api/ai/extract-job-config`
2. 点击 **Try it out**
3. 输入描述：
```
从MySQL的user表同步数据到Hive的ods.user表
```
4. 点击 **Execute**
5. 获得结构化的配置 JSON！

---

## 📋 6个可用的 AI 接口

| 接口 | 功能 | 示例 |
|------|------|------|
| `POST /api/ai/chat` | 💬 通用对话 | "帮我创建一个作业" |
| `POST /api/ai/generate-sql` | 🔧 生成SQL | "查询最近7天订单" |
| `POST /api/ai/extract-job-config` | 📝 提取配置 | "从A表同步到B表" |
| `POST /api/ai/optimize-sql` | ⚡ 优化SQL | 分析性能问题 |
| `POST /api/ai/qa` | 🤔 智能问答 | "如何优化Hive查询" |
| `GET /api/ai/health` | ✅ 健康检查 | 验证服务状态 |

---

## 🔑 如何获取 API Keys

### DeepSeek API Key

1. 访问：https://platform.deepseek.com/
2. 注册/登录账号
3. 点击 **API Keys**
4. 创建新的 Key
5. 复制 Key（格式：`sk-...`）

**特点：**
- ✅ 便宜（¥0.001/千tokens）
- ✅ 代码生成能力强
- ✅ 推理能力优秀

---

### 阿里百炼 API Key

1. 访问：https://dashscope.aliyun.com/
2. 登录阿里云账号
3. 开通 **百炼服务**
4. 创建 API Key
5. 复制 Key（格式：`sk-...`）

**特点：**
- ✅ 中文理解好
- ✅ 多模型选择
- ✅ 稳定可靠

---

## 💰 成本说明

### 免费额度

- **DeepSeek:** 新用户有免费额度
- **阿里百炼:** 开通即送免费额度

### 实际成本

基于典型使用场景：

| 场景 | 月调用量 | DeepSeek | 阿里百炼 | 合计 |
|------|---------|---------|---------|------|
| 个人学习 | 1000次 | ¥1 | ¥5 | **¥6** |
| 小团队 | 5000次 | ¥5 | ¥15 | **¥20** |
| 企业使用 | 50000次 | ¥50 | ¥150 | **¥200** |

> **💡 提示：** 实际成本可能更低，因为有缓存和优化。

---

## 🎨 前端集成示例

### 示例：添加 AI 对话组件

```html
<!-- AI 对话框 -->
<div class="ai-chat-box">
  <input type="text" id="userMessage" placeholder="输入您的需求...">
  <button onclick="sendToAI()">发送</button>
  <div id="aiResponse"></div>
</div>

<script>
async function sendToAI() {
  const message = document.getElementById('userMessage').value;
  
  const response = await fetch('http://localhost:8080/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: message })
  });
  
  const result = await response.json();
  document.getElementById('aiResponse').innerText = result.data.reply;
}
</script>
```

### 示例：SQL 生成按钮

```javascript
async function generateSQL() {
  const requirement = prompt("请描述您的查询需求：");
  
  const response = await fetch('http://localhost:8080/api/ai/generate-sql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      requirement: requirement,
      databaseType: 'MySQL',
      tableName: 'orders'
    })
  });
  
  const result = await response.json();
  document.getElementById('sqlEditor').value = result.data.sql;
}
```

---

## 🐛 常见问题速查

### 问题1：启动时提示找不到 Bean

**错误：**
```
No qualifying bean of type 'ChatClient$Builder'
```

**解决：**
确认 `pom.xml` 中已添加 Spring AI 依赖，然后：
```bash
mvn clean install
```

---

### 问题2：调用 AI 接口返回错误

**错误：**
```
API Key not configured
```

**解决：**
1. 检查环境变量是否设置
2. 重启 IDEA
3. 重新运行应用

---

### 问题3：网络连接超时

**错误：**
```
Connection timeout
```

**解决：**
1. 检查网络连接
2. 确认 API Key 有效
3. 检查防火墙设置

---

## 📚 完整文档目录

学习更多，查看这些文档：

| 文档 | 说明 | 适合人群 |
|------|------|---------|
| `AI_IMPLEMENTATION_COMPLETE.md` | 实施总结 | 所有人 ✅ |
| `AI_TESTING_GUIDE.md` | 详细测试指南 | 开发/测试 |
| `docs/AI_README.md` | 功能介绍 | 决策者 |
| `docs/AI_ARCHITECTURE.md` | 技术架构 | 架构师 |
| `docs/AI_QUICK_START.md` | 30分钟入门 | 开发者 |
| `docs/AI_EXAMPLES.md` | 使用场景 | 产品/运营 |

---

## ✅ 启动检查清单

完成以下步骤，确保一切正常：

- [ ] 已在 IDEA 中设置 API Keys
- [ ] 应用启动无错误
- [ ] `/api/ai/health` 返回 UP
- [ ] Swagger UI 可以访问
- [ ] 测试了至少一个 AI 接口
- [ ] AI 返回了正确的响应

**全部完成？恭喜你！🎉**

---

## 🎯 下一步做什么

### 立即可做
1. 📱 **打开 Swagger UI**
   ```
   http://localhost:8080/swagger-ui.html
   ```

2. 🧪 **测试所有 AI 接口**
   - 尝试不同的提问方式
   - 观察 AI 的回答
   - 调整参数看效果

3. 💻 **集成到前端**
   - 添加 AI 对话按钮
   - 实现 SQL 生成功能
   - 集成配置提取

### 深入学习
1. 📖 阅读 `docs/AI_ARCHITECTURE.md` 了解技术细节
2. 💡 查看 `docs/AI_EXAMPLES.md` 学习更多场景
3. 🚀 实施 RAG、对话历史等高级功能

---

## 🎊 成功！

你现在已经拥有：

✅ 完整的 AI 增强数据平台  
✅ 双模型智能协同（DeepSeek + 阿里百炼）  
✅ 6个强大的 AI 接口  
✅ 完善的文档和示例  

**开始享受 AI 带来的效率提升吧！** 🚀

---

**需要帮助？**
- 查看完整文档：`AI_IMPLEMENTATION_COMPLETE.md`
- 测试指南：`AI_TESTING_GUIDE.md`
- 在 Swagger UI 中直接测试

**Good Luck & Have Fun!** 🎉
