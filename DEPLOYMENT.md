# 阿里云部署指南

## 一、准备工作

### 1.1 云服务资源
- ✅ ECS 服务器（Ubuntu，同 VPC）：101.37.91.222
- ✅ PostgreSQL（内网）：pgm-bp11d4i73j6xz5mf.pg.rds.aliyuncs.com
- ✅ MySQL（内网）：rm-bp1drr56w0kwx946r.mysql.rds.aliyuncs.com
- ✅ Redis（内网）：r-bp1logqxbtf7sexm3.redis.rds.aliyuncs.com
- ✅ 数据已迁移

### 1.2 本地环境
- Java 17+
- Node.js 16+
- Maven 3.6+
- Docker（服务器端）

## 二、本地打包

### 2.1 后端打包
```bash
cd /d/alert4
mvn clean package -DskipTests
```
生成文件：`target/data-platform-1.0.0.jar`

### 2.2 前端打包
```bash
cd /d/alert4/frontend
npm install
npm run build
```
生成目录：`dist/`

## 三、服务器准备

### 3.1 安装 Docker 和 Docker Compose
```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Docker
curl -fsSL https://get.docker.com | bash

# 启动 Docker 服务
sudo systemctl start docker
sudo systemctl enable docker

# 验证安装
docker --version
docker compose version
```

### 3.2 创建部署目录
```bash
mkdir -p /root/data-platform
cd /root/data-platform
```

## 四、上传文件到服务器

使用 SFTP 或 SCP 上传以下文件到 `/root/data-platform/`：

```
/root/data-platform/
├── target/
│   └── data-platform-1.0.0.jar
├── dist/                        # 前端打包产物
│   ├── index.html
│   ├── assets/
│   └── ...
├── Dockerfile                   # 后端 Docker 镜像构建文件
├── docker-compose.yml           # 容器编排配置
├── nginx.conf                   # Nginx 配置
└── .env                         # 环境变量（需创建）
```

### 上传命令示例（本地执行）
```bash
# 使用 SCP 上传
scp -r /d/alert4/target root@101.37.91.222:/root/data-platform/
scp -r /d/alert4/frontend/dist root@101.37.91.222:/root/data-platform/
scp /d/alert4/Dockerfile root@101.37.91.222:/root/data-platform/
scp /d/alert4/docker-compose.yml root@101.37.91.222:/root/data-platform/
scp /d/alert4/nginx.conf root@101.37.91.222:/root/data-platform/
```

## 五、配置环境变量

在服务器上创建 `.env` 文件：

```bash
cd /root/data-platform
nano .env
```

填入以下内容（**必须填写真实的 API Keys**）：
```bash
# 阿里云 DashScope API Key
DASHSCOPE_API_KEY=你的_dashscope_api_key

# DeepSeek API Key
DEEPSEEK_API_KEY=你的_deepseek_api_key

# 邮件服务密码（可选）
MAIL_PASSWORD=ZRYM339mZrztCZyA

# JWT 密钥（建议修改）
JWT_SECRET=your-custom-jwt-secret-at-least-32-characters-long
```

保存并退出（Ctrl+O, Enter, Ctrl+X）

## 六、启动服务

```bash
cd /root/data-platform

# 构建并启动容器
docker compose up -d

# 查看日志
docker compose logs -f

# 查看服务状态
docker compose ps
```

## 七、验证部署

### 7.1 检查服务健康状态
```bash
# 后端健康检查
curl http://localhost/actuator/health

# 前端访问
curl http://101.37.91.222/
```

### 7.2 浏览器访问
- **前端首页**：http://101.37.91.222/
- **API 文档**：http://101.37.91.222/swagger-ui.html
- **健康检查**：http://101.37.91.222/actuator/health

## 八、常用运维命令

### 8.1 容器管理
```bash
# 停止服务
docker compose stop

# 重启服务
docker compose restart

# 停止并删除容器
docker compose down

# 重新构建并启动
docker compose up -d --build
```

### 8.2 日志查看
```bash
# 查看所有服务日志
docker compose logs -f

# 查看后端日志
docker compose logs -f backend

# 查看 Nginx 日志
docker compose logs -f nginx

# 查看应用日志文件
tail -f /root/data-platform/logs/application.log
```

### 8.3 更新应用
```bash
# 1. 停止服务
docker compose down

# 2. 上传新的 jar 包或 dist 目录

# 3. 重新构建并启动
docker compose up -d --build
```

## 九、安全配置建议

### 9.1 配置 HTTPS（推荐）
使用 Let's Encrypt 免费证书：
```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx -y

# 申请证书（需要域名）
sudo certbot --nginx -d yourdomain.com
```

### 9.2 配置防火墙
```bash
# 安装 UFW
sudo apt install ufw -y

# 允许 SSH、HTTP、HTTPS
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 启用防火墙
sudo ufw enable

# 查看状态
sudo ufw status
```

### 9.3 阿里云安全组
在阿里云控制台配置安全组规则：
- 入方向：允许 80（HTTP）、443（HTTPS）、22（SSH）
- 出方向：允许全部

## 十、故障排查

### 10.1 后端无法连接数据库
```bash
# 检查网络连通性
docker exec -it data-platform-backend ping pgm-bp11d4i73j6xz5mf.pg.rds.aliyuncs.com

# 检查环境变量
docker exec -it data-platform-backend env | grep SPRING
```

### 10.2 前端页面空白
```bash
# 检查 dist 目录是否正确上传
ls -la /root/data-platform/dist/

# 检查 Nginx 配置
docker exec -it data-platform-nginx nginx -t

# 查看 Nginx 日志
docker compose logs nginx
```

### 10.3 502 错误
```bash
# 检查后端服务是否启动
docker compose ps

# 检查后端健康状态
docker compose exec backend wget -O- http://localhost:3000/actuator/health

# 查看后端日志
docker compose logs backend
```

## 十一、配置文件说明

### application-prod.yml
- 使用阿里云内网地址连接数据库
- 配置生产级日志（INFO 级别）
- JVM 参数优化（512M-1G）
- 数据库连接池优化

### nginx.conf
- 前端静态资源托管
- API 反向代理到后端
- Gzip 压缩
- 静态资源缓存 7 天

### docker-compose.yml
- 后端服务：Spring Boot 应用
- Nginx 服务：静态资源 + 反向代理
- 健康检查：确保后端启动后再启动 Nginx
- 日志持久化：`./logs` 目录

## 十二、性能优化建议

1. **数据库连接池**：已配置 HikariCP，最大连接数 20
2. **JVM 参数**：使用 G1GC，堆内存 512M-1G
3. **Nginx 缓存**：静态资源缓存 7 天
4. **Gzip 压缩**：减少传输大小
5. **内网通信**：数据库使用内网地址，速度快且免流量费

## 需要的 API Keys

部署前请准备以下 API Keys：
- **DASHSCOPE_API_KEY**：阿里云百炼平台 API Key
- **DEEPSEEK_API_KEY**：DeepSeek API Key

获取方式：
- DashScope：https://dashscope.console.aliyun.com/
- DeepSeek：https://platform.deepseek.com/

---

**预计部署时间**：10-15 分钟（不含文件上传时间）
