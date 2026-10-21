# Docker Compose 部署优化总结

## 📦 已创建的文件清单

### Docker 配置文件
- ✅ `Dockerfile` - 后端多阶段构建配置
- ✅ `docker-compose.yml` - 完整的服务编排配置
- ✅ `.dockerignore` - Docker 构建忽略文件
- ✅ `frontend/Dockerfile` - 前端多阶段构建配置
- ✅ `frontend/.dockerignore` - 前端构建忽略文件
- ✅ `frontend/nginx.conf` - Nginx 反向代理配置

### 环境配置文件
- ✅ `.env.example` - 环境变量模板
- ✅ `src/main/resources/application-prod.yml` - 生产环境配置
- ✅ `.gitignore` - Git 忽略文件（已更新，排除 .env）

### 数据库配置
- ✅ `deploy/mysql/init.sql` - 数据库初始化脚本
- ✅ `deploy/mysql/conf.d/my.cnf` - MySQL 自定义配置

### 部署脚本
- ✅ `deploy.sh` - Linux/Mac 自动化部署脚本
- ✅ `deploy.bat` - Windows 自动化部署脚本
- ✅ `check-docker.sh` - Docker 环境验证脚本

### 文档
- ✅ `DOCKER_DEPLOYMENT.md` - 详细的 Docker 部署文档（9000+ 字）
- ✅ `DEPLOYMENT_CHECKLIST.md` - 部署准备清单和注意事项
- ✅ `PROJECT_STRUCTURE.md` - 项目结构说明
- ✅ `README_DOCKER_CN.md` - 本文档

---

## 🏗️ 架构设计

### 多阶段构建

**后端 (Dockerfile):**
```
阶段1 (builder): Maven 构建 JAR 包
阶段2 (runtime): 仅包含 JRE + JAR，镜像大小优化
```

**前端 (frontend/Dockerfile):**
```
阶段1 (builder): Node.js 构建静态文件
阶段2 (runtime): Nginx 提供静态文件 + API 反向代理
```

### 服务架构

```
┌─────────────────────────────────────────────────┐
│                 Docker Compose                   │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────┐│
│  │   Frontend   │  │   Backend    │  │ MySQL  ││
│  │   (Nginx)    │  │ (Spring Boot)│  │  8.0   ││
│  │              │  │              │  │        ││
│  │ Port: 80     │  │ Port: 8089   │  │ 3306   ││
│  └──────────────┘  └──────────────┘  └────────┘│
│         │                 │                │    │
│         └─────────────────┴────────────────┘    │
│              dataplatform-network                │
└─────────────────────────────────────────────────┘
```

### 网络和数据流

1. **用户访问**: `http://localhost` → Nginx (Port 80)
2. **API 请求**: `/api/*` → Nginx 反向代理 → Backend (Port 8089)
3. **数据库连接**: Backend → MySQL (内部网络，不对外暴露)
4. **健康检查**: Docker 自动检查各服务健康状态

---

## 🔧 关键技术实现

### 1. 环境变量注入

所有敏感配置通过环境变量注入：

```yaml
# docker-compose.yml
backend:
  environment:
    SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}
    SPRING_DATASOURCE_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    JWT_SECRET: ${JWT_SECRET}
```

```yaml
# application-prod.yml
spring:
  datasource:
    password: ${SPRING_DATASOURCE_PASSWORD:root123456}
jwt:
  secret: ${JWT_SECRET:default-secret}
```

### 2. 健康检查

每个服务都配置了健康检查：

```yaml
backend:
  healthcheck:
    test: ["CMD", "wget", "--spider", "http://localhost:8089/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

### 3. 数据持久化

使用 Docker 卷持久化数据：

```yaml
volumes:
  mysql_data:      # 数据库数据
  backend_logs:    # 后端日志
```

### 4. 服务依赖

确保服务按顺序启动：

```yaml
backend:
  depends_on:
    mysql:
      condition: service_healthy  # 等待 MySQL 健康后再启动
```

### 5. Nginx 反向代理

前端 Nginx 代理后端 API：

```nginx
location /api/ {
    proxy_pass http://backend:8089/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

---

## 📋 部署前检查清单

### 服务器要求
- [ ] CPU: 2 核以上
- [ ] 内存: 4GB 以上
- [ ] 磁盘: 20GB 以上
- [ ] 操作系统: Linux/Windows Server

### 软件环境
- [ ] Docker 20.10+ 已安装
- [ ] Docker Compose 2.0+ 已安装
- [ ] 端口 80, 8089, 3306 未被占用

### 配置文件
- [ ] 已复制 `.env.example` 为 `.env`
- [ ] 已修改 `MYSQL_ROOT_PASSWORD`
- [ ] 已修改 `JWT_SECRET` (生产环境必须)
- [ ] 已检查端口配置 (如有冲突需修改)

### 安全配置
- [ ] 数据库密码强度足够 (生产环境)
- [ ] JWT 密钥至少 256 位 (生产环境)
- [ ] 不要将 MySQL 端口暴露到公网
- [ ] 计划配置 HTTPS (生产环境推荐)

---

## 🚀 部署步骤 (快速版)

### Linux/Mac

```bash
# 1. 验证环境
chmod +x check-docker.sh
./check-docker.sh

# 2. 配置环境变量
cp .env.example .env
vim .env  # 修改必要的配置

# 3. 执行部署
chmod +x deploy.sh
./deploy.sh

# 4. 访问应用
# 前端: http://localhost
# 后端: http://localhost:8089
# API 文档: http://localhost:8089/swagger-ui.html
```

### Windows

```cmd
REM 1. 配置环境变量
copy .env.example .env
notepad .env

REM 2. 执行部署
deploy.bat

REM 3. 访问应用
REM 前端: http://localhost
REM 后端: http://localhost:8089
```

---

## 📊 部署后验证

### 1. 检查服务状态

```bash
docker-compose ps
```

期望输出：所有服务状态为 `healthy` 或 `Up`

### 2. 检查健康端点

```bash
# 后端健康检查
curl http://localhost:8089/actuator/health

# 前端健康检查
curl http://localhost/health
```

### 3. 查看日志

```bash
# 实时查看所有日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f mysql
```

### 4. 测试功能

- [ ] 访问前端界面
- [ ] 使用 admin/admin123 登录
- [ ] 测试数据源列表加载
- [ ] 测试创建新作业

---

## 🛠️ 常用运维命令

### 服务管理

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose stop

# 重启服务
docker-compose restart

# 停止并删除容器（保留数据）
docker-compose down

# 停止并删除容器和数据（危险！）
docker-compose down -v
```

### 日志管理

```bash
# 实时日志
docker-compose logs -f [服务名]

# 最近 100 行日志
docker-compose logs --tail=100 [服务名]

# 导出日志到文件
docker-compose logs > logs.txt
```

### 数据备份

```bash
# 备份数据库
docker-compose exec mysql mysqldump -uroot -p$MYSQL_ROOT_PASSWORD data_platform > backup.sql

# 压缩备份
docker-compose exec mysql mysqldump -uroot -p$MYSQL_ROOT_PASSWORD data_platform | gzip > backup_$(date +%Y%m%d).sql.gz

# 恢复数据库
docker-compose exec -T mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD data_platform < backup.sql
```

### 容器管理

```bash
# 进入容器
docker-compose exec backend sh
docker-compose exec mysql bash

# 查看容器资源使用
docker stats

# 清理未使用的资源
docker system prune -a
```

---

## ⚠️ 注意事项

### 生产环境必须修改

1. **数据库密码** - `.env` 中的 `MYSQL_ROOT_PASSWORD`
2. **JWT 密钥** - `.env` 中的 `JWT_SECRET` (至少 256 位)
3. **管理员密码** - 登录后立即修改 admin 账户密码

### 安全建议

1. **不要暴露数据库端口** - 注释掉 `docker-compose.yml` 中 MySQL 的 ports 配置
2. **配置 HTTPS** - 使用 Let's Encrypt 或云服务商证书
3. **限制访问** - 配置防火墙规则
4. **定期备份** - 设置定时备份任务
5. **监控日志** - 配置日志监控和告警

### 资源规划

- **最小配置**: 2 核 CPU + 4GB 内存 + 20GB 磁盘
- **推荐配置**: 4 核 CPU + 8GB 内存 + 50GB 磁盘
- **高负载**: 8 核 CPU + 16GB 内存 + 100GB 磁盘 + SSD

---

## 📚 参考文档

- [Docker 部署详细文档](DOCKER_DEPLOYMENT.md) - 包含故障排查、性能优化等
- [部署准备清单](DEPLOYMENT_CHECKLIST.md) - 详细的部署前准备工作
- [项目结构说明](PROJECT_STRUCTURE.md) - 项目目录和技术栈说明

---

## 🐛 常见问题

### Q1: 端口被占用怎么办？
**A**: 修改 `.env` 中的端口配置：
```properties
FRONTEND_PORT=8080
BACKEND_PORT=8090
MYSQL_PORT=3307
```

### Q2: MySQL 初始化失败？
**A**: 
```bash
# 删除数据卷重新初始化
docker-compose down -v
docker-compose up -d
```

### Q3: 后端连接不上数据库？
**A**: 
```bash
# 检查 MySQL 是否健康
docker-compose exec mysql mysqladmin ping -h localhost

# 检查网络连接
docker-compose exec backend ping mysql
```

### Q4: 前端无法访问后端 API？
**A**: 检查 Nginx 配置和后端服务是否正常：
```bash
# 查看 Nginx 日志
docker-compose logs frontend

# 测试后端 API
curl http://localhost:8089/actuator/health
```

### Q5: 如何更新应用？
**A**:
```bash
git pull
docker-compose up -d --build
docker image prune -f
```

---

## 📞 技术支持

如果遇到问题：

1. 查看 [Docker 部署文档](DOCKER_DEPLOYMENT.md) 的故障排查章节
2. 运行 `./check-docker.sh` 检查环境配置
3. 查看日志: `docker-compose logs -f`
4. 提供以下信息：
   - 操作系统版本
   - Docker 和 Docker Compose 版本
   - 错误日志
   - 配置文件内容（脱敏）

---

**部署成功后请务必：**
1. ✅ 修改默认密码
2. ✅ 配置定期备份
3. ✅ 配置监控告警
4. ✅ 阅读安全建议

**祝部署顺利！** 🎉
