# 快速部署清单

## 本地准备（10 分钟）

### 1. 后端打包
```bash
cd D:\alert4
mvn clean package -DskipTests
```
✅ 生成：`target/data-platform-1.0.0.jar`

### 2. 前端打包
```bash
cd D:\alert4\frontend
npm install
npm run build
```
✅ 生成：`dist/` 目录

### 3. 上传到服务器
使用 WinSCP、FileZilla 或命令行上传以下文件到 `/root/data-platform/`：

```
□ target/data-platform-1.0.0.jar
□ dist/ 目录（整个）
□ Dockerfile
□ docker-compose.yml
□ nginx.conf
□ deploy.sh
```

## 服务器部署（5 分钟）

### 1. 连接服务器
```bash
ssh root@101.37.91.222
```

### 2. 安装 Docker（首次部署）
```bash
curl -fsSL https://get.docker.com | bash
systemctl start docker
systemctl enable docker
```

### 3. 配置环境变量
```bash
cd /root/data-platform
nano .env
```

填入：
```
DASHSCOPE_API_KEY=你的key
DEEPSEEK_API_KEY=你的key
MAIL_PASSWORD=ZRYM339mZrztCZyA
JWT_SECRET=dataplatform-jwt-secret-key-at-least-32-characters-for-hs256-algorithm
```

### 4. 一键部署
```bash
chmod +x deploy.sh
./deploy.sh
```

### 5. 验证
```bash
# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 健康检查
curl http://localhost/actuator/health
```

浏览器访问：http://101.37.91.222/

## 数据库配置（已完成）

✅ PostgreSQL（向量库）：pgm-bp11d4i73j6xz5mf.pg.rds.aliyuncs.com
✅ MySQL（业务库）：rm-bp1drr56w0kwx946r.mysql.rds.aliyuncs.com
✅ Redis（缓存）：r-bp1logqxbtf7sexm3.redis.rds.aliyuncs.com

所有数据库均使用**内网地址**，速度快且免流量费。

## 更新应用

```bash
# 停止服务
docker compose down

# 上传新的 jar 包或 dist 目录

# 重新部署
./deploy.sh
```

## 故障排查

**502 错误**
```bash
docker compose logs backend
```

**前端空白**
```bash
ls -la /root/data-platform/dist/
docker compose logs nginx
```

**数据库连接失败**
```bash
docker exec -it data-platform-backend ping rm-bp1drr56w0kwx946r.mysql.rds.aliyuncs.com
```

## 需要准备的信息

- [x] 服务器 IP：101.37.91.222
- [x] PostgreSQL 内网地址和密码
- [x] MySQL 内网地址和密码
- [x] Redis 内网地址
- [ ] DASHSCOPE_API_KEY
- [ ] DEEPSEEK_API_KEY

## 预计时间

- 本地打包：5 分钟
- 文件上传：3 分钟（取决于网速）
- 服务器部署：5 分钟
- **总计：15 分钟**
