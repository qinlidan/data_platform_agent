# Docker Compose 部署准备清单

## ✅ 已完成的工作

### 1. Docker 配置文件
- ✅ 创建后端 Dockerfile (多阶段构建)
- ✅ 创建前端 Dockerfile (多阶段构建 + Nginx)
- ✅ 创建 docker-compose.yml (包含 MySQL、后端、前端)
- ✅ 创建 .dockerignore 文件
- ✅ 创建 Nginx 配置文件

### 2. 数据库配置
- ✅ 创建 MySQL 初始化脚本 (`deploy/mysql/init.sql`)
- ✅ 创建 MySQL 配置文件 (`deploy/mysql/conf.d/my.cnf`)
- ✅ 配置健康检查

### 3. 环境配置
- ✅ 创建 `.env.example` 环境变量模板
- ✅ 创建生产环境配置 `application-prod.yml`
- ✅ 配置环境变量注入 (JWT_SECRET, 数据库连接等)

### 4. 部署脚本
- ✅ 创建 Linux/Mac 部署脚本 (`deploy.sh`)
- ✅ 创建 Windows 部署脚本 (`deploy.bat`)
- ✅ 创建详细部署文档 (`DOCKER_DEPLOYMENT.md`)

### 5. 应用配置
- ✅ 前端 API 代理配置 (开发环境: Vite proxy, 生产环境: Nginx proxy)
- ✅ 后端支持环境变量配置
- ✅ 健康检查端点配置

## 📋 您需要提供的支持

### 1. 基础环境准备

#### 服务器要求
```
- 操作系统: Linux (推荐 Ubuntu 20.04+) 或 Windows Server
- CPU: 至少 2 核
- 内存: 至少 4GB (推荐 8GB)
- 磁盘: 至少 20GB 可用空间
- 网络: 可访问外网 (用于拉取 Docker 镜像)
```

#### 安装 Docker 和 Docker Compose

**Linux (Ubuntu/Debian):**
```bash
# 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 验证安装
docker --version
docker-compose --version
```

**Windows:**
- 下载安装 Docker Desktop: https://www.docker.com/products/docker-desktop/

### 2. 配置环境变量

复制 `.env.example` 为 `.env` 并修改：

```bash
cp .env.example .env
```

**必须修改的配置项：**

```properties
# 数据库密码（生产环境务必修改）
MYSQL_ROOT_PASSWORD=<your-strong-password>

# JWT 密钥（生产环境务必修改，至少 256 位）
JWT_SECRET=<your-256-bit-secret-key>

# 端口配置（如有冲突请修改）
MYSQL_PORT=3306
BACKEND_PORT=8089
FRONTEND_PORT=80
```

### 3. 网络和端口配置

**需要开放的端口：**
- 80 (前端，HTTP)
- 8089 (后端 API，可选是否对外暴露)
- 3306 (MySQL，建议仅内网访问)

**防火墙配置 (Linux):**
```bash
# 开放 HTTP 端口
sudo ufw allow 80/tcp

# 如需从外部访问后端 API
sudo ufw allow 8089/tcp

# 如需从外部访问数据库（不推荐）
# sudo ufw allow 3306/tcp
```

### 4. 域名和 HTTPS (可选，生产环境推荐)

如果有域名，建议配置 HTTPS：

#### 方案 1: 使用 Nginx + Let's Encrypt

修改 `docker-compose.yml`，添加 Nginx 反向代理容器：

```yaml
nginx-proxy:
  image: nginx:alpine
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./deploy/nginx/nginx.conf:/etc/nginx/nginx.conf
    - ./deploy/nginx/ssl:/etc/nginx/ssl
  depends_on:
    - frontend
```

#### 方案 2: 使用云服务商的负载均衡器
- 阿里云 SLB
- 腾讯云 CLB
- AWS ELB

### 5. 数据备份策略

**创建备份脚本：**

```bash
cat > /opt/dataplatform/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/dataplatform/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# 备份数据库
docker-compose exec -T mysql mysqldump \
  -uroot -p$MYSQL_ROOT_PASSWORD \
  data_platform | gzip > $BACKUP_DIR/db_backup_$DATE.sql.gz

# 清理 7 天前的备份
find $BACKUP_DIR -name "db_backup_*.sql.gz" -mtime +7 -delete

echo "Backup completed: db_backup_$DATE.sql.gz"
EOF

chmod +x /opt/dataplatform/backup.sh
```

**配置定时备份 (crontab):**

```bash
# 每天凌晨 2 点执行备份
0 2 * * * /opt/dataplatform/backup.sh >> /var/log/dataplatform-backup.log 2>&1
```

### 6. 监控和日志

#### 日志管理

**查看实时日志：**
```bash
docker-compose logs -f [服务名]
```

**配置日志轮转：** (防止日志文件过大)

创建 `/etc/docker/daemon.json`:
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
```

重启 Docker:
```bash
sudo systemctl restart docker
```

#### 监控配置 (可选)

推荐工具：
- Prometheus + Grafana
- ELK Stack (Elasticsearch + Logstash + Kibana)
- 云服务商监控 (阿里云 CloudMonitor、腾讯云 CM)

### 7. 安全加固

#### 数据库安全
```bash
# 不要将 MySQL 端口暴露到公网
# 在 docker-compose.yml 中移除或注释掉 MySQL 的 ports 配置

mysql:
  # ports:
  #   - "3306:3306"  # 注释掉这行
```

#### 应用安全
```bash
# 修改默认管理员密码
# 登录后台后立即修改 admin/admin123

# 配置 HTTPS
# 使用 Let's Encrypt 免费证书

# 限制 API 访问频率
# 在 Nginx 或应用层配置 rate limiting
```

## 🚀 部署步骤

### 1. 上传代码到服务器

```bash
# 方式 1: 使用 Git
git clone <your-repo-url>
cd alert4

# 方式 2: 使用 SCP
scp -r alert4/ user@server:/opt/dataplatform/
```

### 2. 配置环境变量

```bash
cd /opt/dataplatform/alert4
cp .env.example .env
vim .env  # 修改配置
```

### 3. 执行部署

**Linux/Mac:**
```bash
chmod +x deploy.sh
./deploy.sh
```

**Windows:**
```cmd
deploy.bat
```

**或手动部署:**
```bash
# 构建并启动
docker-compose up -d --build

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 4. 验证部署

```bash
# 检查服务状态
docker-compose ps

# 检查健康状态
curl http://localhost:8089/actuator/health
curl http://localhost/health

# 访问前端
# 浏览器打开: http://<服务器IP>
```

### 5. 首次登录

- URL: http://<服务器IP>
- 用户名: admin
- 密码: admin123
- **立即修改密码！**

## ⚠️ 常见问题和解决方案

### 问题 1: 端口被占用
```bash
# 修改 .env 中的端口配置
BACKEND_PORT=8090
FRONTEND_PORT=8080
```

### 问题 2: 内存不足
```bash
# 查看资源使用
docker stats

# 清理未使用的资源
docker system prune -a
```

### 问题 3: 数据库初始化失败
```bash
# 查看 MySQL 日志
docker-compose logs mysql

# 重新初始化
docker-compose down -v
docker-compose up -d
```

### 问题 4: 后端连接不上数据库
```bash
# 检查网络连接
docker-compose exec backend ping mysql

# 检查数据库是否就绪
docker-compose exec mysql mysqladmin ping -h localhost
```

## 📞 技术支持

部署过程中如遇到问题，请提供：
1. 操作系统版本
2. Docker 和 Docker Compose 版本
3. 错误日志 (`docker-compose logs`)
4. 配置文件内容 (脱敏后)

---

**注意事项：**
- ⚠️ 生产环境务必修改默认密码和密钥
- ⚠️ 建议配置 HTTPS 和域名
- ⚠️ 定期备份数据库
- ⚠️ 监控服务器资源使用情况
