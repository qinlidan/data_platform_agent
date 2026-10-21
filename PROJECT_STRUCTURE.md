# 数据开发平台 - 项目结构说明

## 📁 项目结构

```
alert4/
├── src/                                 # 后端源码
│   └── main/
│       ├── java/com/dataplatform/
│       │   ├── controller/              # 控制器
│       │   ├── service/                 # 业务逻辑
│       │   ├── mapper/                  # 数据访问
│       │   ├── entity/                  # 实体类
│       │   ├── dto/                     # 数据传输对象
│       │   ├── config/                  # 配置类
│       │   └── security/                # 安全配置
│       └── resources/
│           ├── application.yml          # 开发环境配置
│           ├── application-prod.yml     # 生产环境配置
│           └── db/schema.sql            # 数据库结构
│
├── frontend/                            # 前端源码
│   ├── src/
│   │   ├── views/                       # 页面组件
│   │   ├── components/                  # 通用组件
│   │   ├── api/                         # API 接口
│   │   ├── utils/                       # 工具函数
│   │   └── router/                      # 路由配置
│   ├── Dockerfile                       # 前端 Docker 构建文件
│   ├── nginx.conf                       # Nginx 配置
│   └── package.json                     # 依赖管理
│
├── deploy/                              # 部署相关
│   └── mysql/
│       ├── init.sql                     # 数据库初始化脚本
│       └── conf.d/my.cnf                # MySQL 配置
│
├── Dockerfile                           # 后端 Docker 构建文件
├── docker-compose.yml                   # Docker Compose 编排
├── .env.example                         # 环境变量模板
├── .dockerignore                        # Docker 忽略文件
├── deploy.sh                            # Linux/Mac 部署脚本
├── deploy.bat                           # Windows 部署脚本
├── DOCKER_DEPLOYMENT.md                 # Docker 部署详细文档
├── DEPLOYMENT_CHECKLIST.md              # 部署准备清单
└── pom.xml                              # Maven 配置
```

## 🔧 技术栈

### 后端
- **框架**: Spring Boot 3.2.2
- **数据库**: MySQL 8.0 + MyBatis-Plus
- **安全**: Spring Security + JWT
- **API 文档**: SpringDoc (Swagger UI)
- **构建工具**: Maven 3.9+
- **Java**: 17

### 前端
- **框架**: Vue 3
- **UI 组件**: Element Plus
- **构建工具**: Vite
- **HTTP 客户端**: Axios
- **路由**: Vue Router

### 部署
- **容器化**: Docker
- **编排**: Docker Compose
- **Web 服务器**: Nginx (前端)
- **Java 运行时**: Eclipse Temurin (OpenJDK)

## 🚀 快速开始

### 本地开发

#### 后端
```bash
# 启动 MySQL (使用 Docker)
docker-compose up -d mysql

# 运行后端
cd alert4
mvn spring-boot:run
```

#### 前端
```bash
cd frontend
npm install
npm run dev
```

访问: http://localhost:3000

### Docker 部署

详细步骤请参考:
- [Docker 部署文档](DOCKER_DEPLOYMENT.md)
- [部署准备清单](DEPLOYMENT_CHECKLIST.md)

**快速部署 (Linux/Mac):**
```bash
cp .env.example .env
# 编辑 .env 文件
chmod +x deploy.sh
./deploy.sh
```

**快速部署 (Windows):**
```cmd
copy .env.example .env
REM 编辑 .env 文件
deploy.bat
```

## 📝 核心功能

1. **数据源管理** - 支持多种数据源类型 (MySQL, Oracle, DB2, Hive 等)
2. **ETL 作业管理** - 作业创建、版本管理、发布
3. **SQL 编辑器** - 支持 SQL 语法检查、AI 辅助
4. **字段元数据** - 自动解析和管理字段信息
5. **作业调度** - 作业执行和监控 (计划中)

## 🔐 默认账户

- 用户名: `admin`
- 密码: `admin123`

**⚠️ 生产环境请务必修改默认密码！**

## 📚 API 文档

部署后访问: http://localhost:8089/swagger-ui.html

## 🛠️ 开发指南

### 添加新的 API 端点

1. 创建 Entity (实体类)
2. 创建 Mapper (数据访问接口)
3. 创建 Service (业务逻辑)
4. 创建 Controller (接口控制器)
5. 更新前端 API 配置

### 添加新的前端页面

1. 在 `frontend/src/views` 创建页面组件
2. 在 `frontend/src/router` 添加路由
3. 在 `frontend/src/api` 添加 API 调用

### 数据库变更

1. 修改 `deploy/mysql/init.sql`
2. 更新对应的 Entity 和 Mapper
3. 重新部署或手动执行 SQL

## 🐛 故障排查

### 常见问题

1. **端口冲突**: 修改 `.env` 中的端口配置
2. **数据库连接失败**: 检查 MySQL 是否已启动
3. **前端无法访问后端**: 检查 Nginx 代理配置
4. **内存不足**: 调整 Docker 资源限制

详细问题和解决方案请参考 [部署文档](DOCKER_DEPLOYMENT.md#故障排查)

## 📦 构建和打包

### 后端打包
```bash
mvn clean package -DskipTests
# 输出: target/data-platform-0.0.1-SNAPSHOT.jar
```

### 前端打包
```bash
cd frontend
npm run build
# 输出: frontend/dist/
```

### Docker 镜像构建
```bash
# 构建所有服务
docker-compose build

# 构建单个服务
docker-compose build backend
docker-compose build frontend
```

## 🔄 更新和维护

### 应用更新
```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建并部署
docker-compose up -d --build

# 3. 清理旧镜像
docker image prune -f
```

### 数据库备份
```bash
# 备份
docker-compose exec mysql mysqldump -uroot -p data_platform > backup.sql

# 恢复
docker-compose exec -T mysql mysql -uroot -p data_platform < backup.sql
```

### 日志查看
```bash
# 实时日志
docker-compose logs -f

# 特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f mysql
```

## 📊 监控建议

### 应用层监控
- Spring Boot Actuator (已配置)
- Prometheus + Grafana

### 基础设施监控
- Docker stats
- 云服务商监控工具

### 日志监控
- ELK Stack
- Loki + Grafana

## 🔒 安全建议

1. **修改默认密码** - 数据库、管理员账户
2. **使用 HTTPS** - 配置 SSL 证书
3. **限制端口暴露** - 不要将数据库端口暴露到公网
4. **定期更新** - 及时更新依赖和镜像
5. **备份数据** - 定期备份数据库
6. **监控日志** - 关注异常访问和错误日志

## 📄 许可证

[根据实际情况填写]

## 🤝 贡献

[根据实际情况填写]

## 📧 联系方式

[根据实际情况填写]

---

**文档更新日期**: 2026-10-15
