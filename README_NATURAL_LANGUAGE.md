# 数据平台 - 自然语言作业管理系统

## 🎉 项目概述

这是一个基于 Spring Boot + Vue 3 的数据同步作业管理平台，支持通过自然语言创建和管理数据同步作业。

### ✨ 核心特性

- 🤖 **AI 自然语言交互** - 使用 Spring AI 的 @Tool 方式实现智能作业管理
- 📊 **作业全生命周期管理** - 创建、配置、发布、归档
- 🔍 **SQL 智能解析** - AI 语法检查和字段自动提取
- 🎨 **现代化 UI** - 深色主题，流畅交互

---

## 🏗️ 技术栈

### 后端
- **Spring Boot 3.x** - 核心框架
- **Spring AI** - AI 集成（Function Calling）
- **MyBatis Plus** - ORM 框架
- **MySQL** - 数据存储
- **阿里百炼** - AI 大模型

### 前端
- **Vue 3** - 前端框架
- **Vite** - 构建工具
- **Element Plus** - UI 组件库
- **Axios** - HTTP 客户端

---

## 📦 项目结构

```
alert4/
├── src/main/java/com/dataplatform/
│   ├── ai/
│   │   └── tools/
│   │       └── JobManagementTools.java        # @Tool 工具类
│   ├── controller/
│   │   ├── NaturalLanguageController.java     # 自然语言接口
│   │   ├── JobController.java                 # 作业管理接口
│   │   └── ...
│   ├── service/
│   │   ├── NaturalLanguageJobService.java     # 自然语言服务
│   │   ├── JobService.java                    # 作业服务
│   │   └── ...
│   └── dto/
│       ├── JobOperationRequest.java           # 请求 DTO
│       └── JobOperationResponse.java          # 响应 DTO
│
└── frontend/
    ├── src/
    │   ├── views/
    │   │   ├── JobList.vue                    # 作业列表页
    │   │   └── JobCreateDialog.vue            # 创建作业对话框
    │   ├── api/
    │   │   └── index.js                       # API 接口
    │   ├── router/
    │   │   └── index.js                       # 路由配置
    │   └── utils/
    │       └── request.js                     # Axios 封装
    └── package.json
```

---

## 🚀 快速开始

### 1️⃣ 启动后端

```bash
cd D:/alert4
mvn clean install
mvn spring-boot:run
```

后端将运行在：`http://localhost:8080`

### 2️⃣ 启动前端

```bash
cd D:/alert4/frontend
npm install
npm run dev
```

前端将运行在：`http://localhost:3000`

### 3️⃣ 访问系统

打开浏览器访问：`http://localhost:3000`

---

## 🎯 功能模块

### 1. 作业列表页

**功能特性：**
- ✅ 多条件搜索（作业名称、状态、类型）
- ✅ 作业状态展示（草稿、已发布、已归档）
- ✅ 快速操作（编辑、发布、归档、删除）
- ✅ 分页加载

**访问路径：** `/job/list`

---

### 2. 作业创建流程（三步骤）

#### Step 1: 参数配置
- 作业编码、名称、类型
- 源数据源选择（DB2HIVE 类型）
- 源/目标数据库和表名
- 执行引擎选择（Hive/Spark）

#### Step 2: SQL 配置
- SQL 语句编辑
- **语法检查** - AI 自动检查 SQL 语法
- **字段解析** - 自动提取 SQL 中的字段
- Spark 配置（可选）

#### Step 3: 字段元数据
- 字段英文名、中文名
- 字段类型
- 是否主键、是否分区键
- **AI 智能填充** - 自动生成字段元数据

---

### 3. 自然语言交互（AI）

#### 后端实现 - Spring AI @Tool 方式

```java
@Component
public class JobManagementTools {
    
    @Tool(description = "创建数据同步作业")
    public Job createJob(JobCreateRequest request) {
        return jobService.create(request);
    }
    
    @Tool(description = "修改作业配置")
    public Job updateJob(Long jobId, JobUpdateRequest request) {
        return jobService.update(jobId, request);
    }
    
    // ... 更多工具方法
}
```

#### API 接口

```bash
POST /api/nl/chat
Content-Type: application/json

{
  "message": "帮我创建一个从 MySQL user 表同步到 Hive ods.user 表的作业"
}
```

**AI 自动：**
1. 理解用户意图
2. 选择合适的工具方法
3. 提取并填充参数
4. 执行作业创建

---

## 🔧 核心 API 接口

### 作业管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/job/page` | GET | 分页查询作业列表 |
| `/api/job` | POST | 创建作业 |
| `/api/job/{id}` | PUT | 更新作业 |
| `/api/job/{id}` | DELETE | 删除作业 |
| `/api/job/{id}/publish` | POST | 发布作业 |
| `/api/job/{id}/archive` | POST | 归档作业 |

### 作业版本

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/job/{jobId}/versions` | GET | 获取版本列表 |
| `/api/job/{jobId}/sql` | POST | 配置 SQL |
| `/api/job/version/{id}/publish` | POST | 发布版本 |

### 字段元数据

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/job/version/{versionId}/fields` | GET | 获取字段列表 |
| `/api/job/version/{versionId}/fields` | POST | 保存字段元数据 |
| `/api/field/parse` | POST | 从 SQL 解析字段 |

### AI 辅助

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ai/check-sql-syntax` | POST | SQL 语法检查 |
| `/api/ai/generate-field-metadata` | POST | 生成字段元数据 |
| `/api/nl/chat` | POST | 自然语言对话 |

---

## 🎨 UI 设计

### 配色方案
- **背景色：** `#0B0E14` (深色海军蓝)
- **卡片背景：** `#1E293B` (深灰)
- **文字颜色：** `#F8FAFC` (亮白)
- **主色调：** `#38BDF8` (天蓝色)
- **成功色：** `#22D3EE` (青色)
- **警告色：** `#F59E0B` (橙色)

### 设计特点
- 深色主题，专业感强
- 扁平化设计，现代简洁
- 流畅的交互动画
- 清晰的信息层级

---

## 📝 使用示例

### 示例 1: 通过界面创建作业

1. 点击"新建作业"按钮
2. **Step 1** 填写作业基本信息
   - 作业编码：`sync_user_data`
   - 作业名称：`用户数据同步`
   - 作业类型：`DB2HIVE`
   - 源数据源：选择 MySQL 数据源
   - 源表：`test.user`
   - 目标表：`ods.user`
3. **Step 2** 配置 SQL
   ```sql
   SELECT 
     id, username, email, phone, 
     create_time, update_time
   FROM test.user
   WHERE dt = '${bizdate}'
   ```
   - 点击"语法检查" ✅
   - 点击"解析字段" - 自动识别 6 个字段
4. **Step 3** 填写字段元数据
   - 手动填写中文名，或点击"AI智能填充"
   - 标记 `id` 为主键
5. 点击"保存并发布"完成！

### 示例 2: 通过自然语言创建作业

```bash
POST /api/nl/chat
{
  "message": "创建一个作业，从 MySQL 的 test.orders 表同步到 Hive 的 ods.orders 表，使用 Spark 引擎"
}
```

**AI 自动处理：**
- 识别意图：创建作业
- 提取参数：作业类型 DB2HIVE、源表 test.orders、目标表 ods.orders、引擎 SPARK
- 调用创建接口
- 返回创建结果

---

## 🔍 后续优化建议

### 功能增强
- [ ] 作业执行日志查看
- [ ] 作业调度配置（定时任务）
- [ ] 数据血缘关系图
- [ ] 作业性能监控
- [ ] 批量操作支持

### 技术优化
- [ ] 前端状态管理（Pinia）
- [ ] 接口请求缓存
- [ ] WebSocket 实时推送
- [ ] 单元测试覆盖
- [ ] Docker 容器化部署

---

## 📄 许可证

MIT License

---

## 👥 贡献者

- 开发者：Claude + 用户

---

## 🎊 完成状态

### ✅ 已完成
- 后端 Spring AI @Tool 实现
- 前端 Vue 3 + Element Plus 界面
- 作业列表查询和筛选
- 作业创建三步骤流程
- SQL 语法检查和解析
- 字段元数据管理
- 深色主题 UI

### 🚀 可以开始使用了！

**前端地址：** http://localhost:3000  
**后端地址：** http://localhost:8080  
**API 文档：** http://localhost:8080/swagger-ui.html

---

**Enjoy! 🎉**
