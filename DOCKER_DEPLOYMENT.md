# 数据开发平台 Docker 部署指南

## 目录结构

```
alert4/
├── Dockerfile                    # 后端 Dockerfile
├── docker-compose.yml            # Docker Compose 编排文件
├── .env.example                  # 环境变量模板
├── .dockerignore                 # Docker 忽略文件
├── deploy.sh                     # Linux/Mac 部署脚本
├── deploy.bat                    # Windows 部署脚本
├── deploy/
│   └── mysql/
│       ├── init.sql              # 数据库初始化脚本
│       └── conf.d/
│           └── my.cnf            # MySQL 配置文件
└── frontend/
    ├── Dockerfile                # 前端 Dockerfile
    ├── nginx.conf                # Nginx 配置文件
    └── .dockerignore             # 前端 Docker 忽略文件
```

## 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

### 1. 配置环境变量

复制环境变量模板并编辑：

```bash
cp .env.example .env
```

编辑 `.env` 文件，修改以下关键配置：

```properties
# MySQL 配置
MYSQL_ROOT_PASSWORD=your_strong_password_here
MYSQL_DATABASE=data_platform

# JWT 配置（生产环境务必修改）
JWT_SECRET=your_256_bit_secret_key_change_this_in_production

# 端口配置（如有端口冲突可修改）
MYSQL_PORT=3306
BACKEND_PORT=8089
FRONTEND_PORT=80
```

### 2. 部署应用

#### Linux/Mac

```bash
chmod +x deploy.sh
./deploy.sh
```

#### Windows

双击运行 `deploy.bat` 或在 PowerShell 中执行：

```powershell
.\deploy.bat
```

#### 手动部署

```bash
# 1. 构建并启动所有服务
docker-compose up -d --build

# 2. 查看服务状态
docker-compose ps

# 3. 查看日志
docker-compose logs -f
```

### 3. 访问应用

- **前端界面**: http://localhost:80
- **后端 API**: http://localhost:8089
- **API 文档**: http://localhost:8089/swagger-ui.html
- **健康检查**: http://localhost:8089/actuator/health

**默认账户**:
- 用户名: `admin`
- 密码: `admin123`

## 常用命令

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f mysql

# 查看最近 100 行日志
docker-compose logs --tail=100 backend
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend
```

### 停止服务

```bash
# 停止所有服务（保留数据）
docker-compose stop

# 停止并删除容器（保留数据）
docker-compose down

# 停止并删除容器和数据卷（危险操作）
docker-compose down -v
```

### 进入容器

```bash
# 进入后端容器
docker-compose exec backend sh

# 进入 MySQL 容器
docker-compose exec mysql bash

# 连接 MySQL 数据库
docker-compose exec mysql mysql -uroot -p
```

### 更新应用

```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建并启动
docker-compose up -d --build

# 3. 清理旧镜像
docker image prune -f
```

## 服务配置

### 后端服务

- **容器名**: dataplatform-backend
- **端口**: 8089 (可通过 BACKEND_PORT 修改)
- **日志目录**: `backend_logs` 卷 (映射到 `/app/logs`)
- **健康检查**: 每 30 秒检查一次，启动后 60 秒开始

### 前端服务

- **容器名**: dataplatform-frontend
- **端口**: 80 (可通过 FRONTEND_PORT 修改)
- **反向代理**: Nginx 代理 `/api/*` 到后端服务
- **健康检查**: 每 30 秒检查一次

### 数据库服务

- **容器名**: dataplatform-mysql
- **端口**: 3306 (可通过 MYSQL_PORT 修改)
- **数据卷**: `mysql_data` (持久化存储)
- **字符集**: utf8mb4
- **时区**: Asia/Shanghai
- **健康检查**: 每 10 秒检查一次

## 数据持久化

项目使用 Docker 卷持久化数据：

```bash
# 查看所有卷
docker volume ls | grep alert4

# 备份数据库
docker-compose exec mysql mysqldump -uroot -p data_platform > backup.sql

# 恢复数据库
docker-compose exec -T mysql mysql -uroot -p data_platform < backup.sql
```

## 性能优化

### 后端优化

在 `docker-compose.yml` 中调整后端资源限制：

```yaml
backend:
  deploy:
    resources:
      limits:
        cpus: '2'
        memory: 2G
      reservations:
        memory: 512M
```

### MySQL 优化

在 `deploy/mysql/conf.d/my.cnf` 中调整 MySQL 配置：

```ini
innodb_buffer_pool_size=2G  # 根据可用内存调整
max_connections=500          # 根据并发需求调整
```

## 生产环境建议

### 安全配置

1. **修改默认密码**
   - 修改 `.env` 中的 `MYSQL_ROOT_PASSWORD`
   - 修改 `JWT_SECRET`（至少 256 位）
   - 登录后立即修改 admin 账户密码

2. **使用 HTTPS**
   - 配置 SSL 证书
   - 更新 `frontend/nginx.conf` 添加 HTTPS 支持

3. **限制端口暴露**
   - 生产环境不要暴露 MySQL 端口到公网
   - 使用防火墙限制访问

### 监控与日志

1. **日志收集**
   - 配置日志收集工具（如 ELK Stack）
   - 定期清理旧日志

2. **监控指标**
   - 使用 Prometheus + Grafana 监控服务
   - 监控 CPU、内存、磁盘使用率
   - 监控数据库连接数、慢查询

### 备份策略

```bash
# 创建定时备份脚本
cat > backup.sh << 'EOF'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
docker-compose exec -T mysql mysqldump -uroot -p$MYSQL_ROOT_PASSWORD data_platform | gzip > backup_$DATE.sql.gz
# 保留最近 7 天的备份
find . -name "backup_*.sql.gz" -mtime +7 -delete
EOF

chmod +x backup.sh

# 添加到 crontab（每天凌晨 2 点备份）
echo "0 2 * * * /path/to/backup.sh" | crontab -
```

## 故障排查

### 常见问题

#### 1. 端口被占用

```bash
# 查看端口占用
netstat -tuln | grep :8089

# 修改 .env 中的端口配置
BACKEND_PORT=8090
```

#### 2. 容器启动失败

```bash
# 查看详细日志
docker-compose logs backend

# 检查容器状态
docker-compose ps
```

#### 3. 数据库连接失败

```bash
# 检查 MySQL 是否就绪
docker-compose exec mysql mysqladmin ping -h localhost

# 检查网络连接
docker-compose exec backend ping mysql
```

#### 4. 内存不足

```bash
# 查看资源使用情况
docker stats

# 清理未使用的资源
docker system prune -a
```

### 日志位置

- **后端日志**: `backend_logs` 卷 (`/app/logs/application.log`)
- **前端日志**: `docker-compose logs frontend`
- **数据库日志**: `docker-compose logs mysql`

## 开发环境

如需在开发环境使用 Docker：

```bash
# 仅启动 MySQL
docker-compose up -d mysql

# 本地运行后端和前端
cd alert4
mvn spring-boot:run

cd frontend
npm run dev
```

## 技术栈

- **后端**: Spring Boot 3.2.2 + Java 17
- **前端**: Vue 3 + Element Plus + Vite
- **数据库**: MySQL 8.0
- **Web 服务器**: Nginx 1.25
- **容器化**: Docker + Docker Compose

## 支持

如有问题，请查看：
- 项目文档: `/docs`
- API 文档: http://localhost:8089/swagger-ui.html
- 日志文件: `docker-compose logs`

---

**注意**: 首次部署时，数据库初始化可能需要 1-2 分钟，请耐心等待服务就绪。
