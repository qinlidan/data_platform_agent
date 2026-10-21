# 🚀 数据平台管理系统 - AI 增强版

> 基于 Spring Boot 3.2 + Spring AI 的智能数据平台管理系统

## ✨ 核心特性

### 📊 基础功能
- ✅ 数据源管理（MySQL、Hive 等）
- ✅ 作业管理（创建、配置、版本控制）
- ✅ 字段元数据管理
- ✅ 用户认证和权限管理
- ✅ 完整的 REST API

### 🤖 AI 增强功能（NEW！）
- 🎯 **自然语言创建作业** - "从 MySQL 的 user 表同步到 Hive"
- 💡 **智能 SQL 生成** - "查询最近7天的订单，按金额降序"
- 🔧 **配置智能提取** - 自动识别并提取作业配置
- ⚡ **SQL 优化建议** - 分析性能并给出优化方案
- 💬 **智能问答助手** - 回答技术问题和故障诊断
- 🌟 **双模型协同** - DeepSeek（代码生成）+ 阿里百炼（对话理解）

---

## 🎯 快速开始

### 1️⃣ 环境要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- IDEA 2023+ (推荐)

### 2️⃣ 获取 API Keys

**DeepSeek:**
- 访问：https://platform.deepseek.com/
- 注册并创建 API Key

**阿里百炼:**
- 访问：https://dashscope.aliyun.com/
- 开通服务并创建 API Key

### 3️⃣ 配置环境变量

在 IDEA 的 Run Configuration 中设置：
```
DEEPSEEK_API_KEY=你的DeepSeek_Key
ALI_AI_KEY=你的阿里云Key
```

### 4️⃣ 启动应用

```bash
# 克隆项目
git clone <repository-url>
cd alert4

# 安装依赖
mvn clean install

# 启动应用
mvn spring-boot:run
```

### 5️⃣ 访问应用

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **AI 健康检查:** http://localhost:8080/api/ai/health
- **Actuator:** http://localhost:8080/actuator/health

---

## 📖 文档导航

### 🚀 快速开始
| 文档 | 说明 | 阅读时间 |
|------|------|---------|
| [QUICK_START_AI.md](QUICK_START_AI.md) | 5分钟快速启动 | 5分钟 ⭐ |
| [AI_TESTING_GUIDE.md](AI_TESTING_GUIDE.md) | 完整测试指南 | 15分钟 |
| [AI_IMPLEMENTATION_COMPLETE.md](AI_IMPLEMENTATION_COMPLETE.md) | 实施总结 | 10分钟 |

### 📚 详细文档
| 文档 | 说明 | 适合人群 |
|------|------|---------|
| [docs/AI_README.md](docs/AI_README.md) | AI 功能总览 | 决策者 |
| [docs/AI_ARCHITECTURE.md](docs/AI_ARCHITECTURE.md) | 技术架构设计 | 架构师 |
| [docs/AI_QUICK_START.md](docs/AI_QUICK_START.md) | 30分钟实施指南 | 开发者 |
| [docs/AI_EXAMPLES.md](docs/AI_EXAMPLES.md) | 20+ 使用场景 | 产品/测试 |

### 🔧 其他文档
| 文档 | 说明 |
|------|------|
| [SWAGGER_GUIDE.md](SWAGGER_GUIDE.md) | Swagger API 文档 |
| [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) | 完整文档索引 |

---

## 🎨 技术栈

### 后端框架
- **Spring Boot 3.2** - 核心框架
- **Spring AI** - AI 能力集成
- **Spring Security** - 安全认证
- **MyBatis-Plus** - 数据库 ORM

### AI 模型
- **DeepSeek** - 代码生成、SQL 生成、推理任务
- **阿里百炼 (通义千问)** - 对话理解、意图识别

### 其他
- **JWT** - 令牌认证
- **SpringDoc OpenAPI** - API 文档
- **Lombok** - 代码简化

---

## 🌟 AI 功能演示

### 示例1：自然语言创建作业

**输入：**
```
从 MySQL 的 orders 表同步到 Hive 的 ods.orders 表，
只同步最近7天的数据
```

**AI 自动提取：**
```json
{
  "jobName": "订单数据同步",
  "jobType": "DB2HIVE",
  "sourceTable": "orders",
  "targetTable": "ods.orders",
  "filterCondition": "最近7天",
  "sqlContent": "SELECT * FROM orders WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)"
}
```

---

### 示例2：智能 SQL 生成

**输入：**
```
统计每个部门的平均工资，按工资降序，只显示前10名
```

**AI 生成：**
```sql
SELECT 
    department_id,
    department_name,
    AVG(salary) as avg_salary,
    COUNT(*) as employee_count
FROM employees
GROUP BY department_id, department_name
ORDER BY avg_salary DESC
LIMIT 10;
```

---

### 示例3：SQL 优化建议

**输入 SQL：**
```sql
SELECT * FROM orders WHERE status = '已完成';
```

**AI 建议：**
```json
{
  "issues": [
    "使用 SELECT * 会查询所有列，影响性能",
    "status 字段建议添加索引"
  ],
  "suggestions": [
    "明确指定需要的列",
    "CREATE INDEX idx_status ON orders(status)"
  ],
  "optimizedSql": "SELECT id, order_no, amount, create_time FROM orders WHERE status = '已完成'",
  "improvement": "预计性能提升 30-50%"
}
```

---

## 📡 API 接口

### 基础接口（已有）

#### 认证接口
- `POST /api/auth/login` - 登录
- `POST /api/auth/register` - 注册

#### 数据源管理
- `POST /api/datasources` - 创建数据源
- `GET /api/datasources/list` - 数据源列表
- `POST /api/datasources/{id}/test` - 测试连接

#### 作业管理
- `POST /api/jobs` - 创建作业
- `GET /api/jobs/list` - 作业列表
- `POST /api/jobs/{id}/publish` - 发布作业

### AI 接口（新增）

#### AI 助手
- `POST /api/ai/chat` - 💬 通用对话
- `POST /api/ai/generate-sql` - 🔧 生成 SQL
- `POST /api/ai/extract-job-config` - 📝 提取配置
- `POST /api/ai/optimize-sql` - ⚡ 优化 SQL
- `POST /api/ai/qa` - 🤔 智能问答
- `GET /api/ai/health` - ✅ 健康检查

---

## 🏗️ 项目结构

```
alert4/
├── src/main/java/com/dataplatform/
│   ├── ai/                         # AI 相关
│   │   └── PromptTemplates.java    # 提示词模板
│   ├── config/                     # 配置类
│   │   ├── AIConfig.java           # AI 配置
│   │   ├── SecurityConfig.java     # 安全配置
│   │   └── SwaggerConfig.java      # Swagger 配置
│   ├── controller/                 # 控制器
│   │   ├── AIController.java       # AI 接口
│   │   ├── JobController.java      # 作业管理
│   │   └── ...
│   ├── dto/                        # 数据传输对象
│   │   ├── ChatRequest.java
│   │   ├── ChatResponse.java
│   │   └── ...
│   ├── entity/                     # 实体类
│   ├── service/                    # 服务层
│   │   ├── AIAssistantService.java # AI 助手服务
│   │   └── ...
│   └── DataPlatformApplication.java
├── src/main/resources/
│   ├── application.yml             # 配置文件
│   └── ...
├── docs/                           # 详细文档
│   ├── AI_README.md
│   ├── AI_ARCHITECTURE.md
│   ├── AI_QUICK_START.md
│   └── AI_EXAMPLES.md
├── QUICK_START_AI.md               # 快速启动
├── AI_TESTING_GUIDE.md             # 测试指南
├── AI_IMPLEMENTATION_COMPLETE.md   # 实施总结
└── pom.xml
```

---

## 💰 成本分析

### 开发成本
- ✅ **已完成实施** - 无额外开发成本

### 运行成本（月度）

| 使用场景 | DeepSeek | 阿里百炼 | 月总成本 |
|---------|---------|---------|---------|
| 个人学习 | ¥1 | ¥5 | **¥6** |
| 小团队(10人) | ¥5 | ¥15 | **¥20** |
| 中型企业(100人) | ¥50 | ¥150 | **¥200** |

### ROI 分析

**传统方式：**
- 配置一个作业：5-10 分钟
- 编写复杂 SQL：10-30 分钟
- 排查问题：30-60 分钟

**使用 AI 后：**
- 配置作业：30 秒 ⚡
- 生成 SQL：10 秒 ⚡
- 问题诊断：5 分钟 ⚡

**效率提升：90%+** 🚀

---

## 🧪 测试

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=AIAssistantServiceTest
```

### 手动测试

访问 Swagger UI 进行交互式测试：
```
http://localhost:8080/swagger-ui.html
```

---

## 📊 监控和日志

### 健康检查

```bash
# 应用健康
curl http://localhost:8080/actuator/health

# AI 服务健康
curl http://localhost:8080/api/ai/health
```

### 日志级别

在 `application.yml` 中配置：
```yaml
logging:
  level:
    com.dataplatform: debug
    org.springframework.ai: debug
```

---

## 🤝 贡献指南

欢迎贡献！请遵循以下步骤：

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📝 待办事项

### 短期 (1-2周)
- [ ] 对话历史管理（Redis）
- [ ] 多轮对话上下文
- [ ] 前端 UI 集成
- [ ] 调用统计和监控

### 中期 (1个月)
- [ ] RAG 检索增强
- [ ] 向量数据库集成
- [ ] 智能路由优化
- [ ] 批量操作支持

### 长期 (3个月)
- [ ] 自定义模型微调
- [ ] 企业知识库
- [ ] 工作流自动化
- [ ] 智能推荐系统

---

## 📜 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 🙋 FAQ

### Q: 必须要 API Key 才能运行吗？
A: 基础功能不需要，但 AI 功能需要。可以先用默认配置启动，测试基础功能。

### Q: 支持哪些数据库？
A: 目前支持 MySQL、Hive、Spark SQL。

### Q: AI 功能的成本高吗？
A: 非常低！个人使用每月约 ¥6，小团队约 ¥20。

### Q: 可以使用其他 AI 模型吗？
A: 可以！Spring AI 支持 OpenAI、Azure OpenAI 等多种模型。

### Q: 如何集成到现有项目？
A: 参考 `docs/AI_ARCHITECTURE.md` 的集成方案。

---

## 📞 联系方式

- **文档:** 查看 `docs/` 目录
- **Issues:** GitHub Issues
- **讨论:** GitHub Discussions

---

## 🎉 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring AI](https://spring.io/projects/spring-ai)
- [DeepSeek](https://www.deepseek.com/)
- [阿里云百炼](https://dashscope.aliyun.com/)
- [MyBatis-Plus](https://baomidou.com/)

---

## ⭐ Star History

如果这个项目对你有帮助，请给一个 Star ⭐

---

**开始你的 AI 数据平台之旅！** 🚀

👉 **立即开始:** 阅读 [QUICK_START_AI.md](QUICK_START_AI.md)
