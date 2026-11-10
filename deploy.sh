#!/bin/bash

# 数据平台一键部署脚本
# 使用方法：./deploy.sh

set -e

echo "======================================"
echo "  数据平台部署脚本"
echo "======================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否在正确的目录
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${RED}错误：请在项目根目录运行此脚本${NC}"
    exit 1
fi

# 检查 .env 文件
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}警告：未找到 .env 文件${NC}"
    echo "请根据 .env.example 创建 .env 文件并填写必要的 API Keys"
    echo ""
    read -p "是否继续部署？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# 检查必要文件
echo -e "${GREEN}[1/6] 检查必要文件...${NC}"
if [ ! -d "dist" ]; then
    echo -e "${RED}错误：未找到 dist 目录，请先运行前端打包${NC}"
    exit 1
fi

if ! ls target/*.jar >/dev/null 2>&1; then
    echo -e "${RED}错误：未找到 target/*.jar，请先运行后端打包${NC}"
    exit 1
fi

if [ ! -f "Dockerfile" ]; then
    echo -e "${RED}错误：未找到 Dockerfile${NC}"
    exit 1
fi

if [ ! -f "nginx.conf" ]; then
    echo -e "${RED}错误：未找到 nginx.conf${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 文件检查通过${NC}"
echo ""

# 检查 Docker 是否安装
echo -e "${GREEN}[2/6] 检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误：Docker 未安装${NC}"
    echo "请先安装 Docker：curl -fsSL https://get.docker.com | bash"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo -e "${RED}错误：Docker Compose 未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker 环境正常${NC}"
echo ""

# 停止旧容器
echo -e "${GREEN}[3/6] 停止旧容器...${NC}"
if docker compose ps | grep -q "Up"; then
    docker compose down
    echo -e "${GREEN}✓ 旧容器已停止${NC}"
else
    echo "无运行中的容器"
fi
echo ""

# 清理旧镜像（可选）
echo -e "${GREEN}[4/6] 清理旧镜像...${NC}"
docker image prune -f
echo -e "${GREEN}✓ 清理完成${NC}"
echo ""

# 构建并启动服务
echo -e "${GREEN}[5/6] 构建并启动服务...${NC}"
docker compose up -d --build

# 等待服务启动
echo ""
echo -e "${GREEN}[6/6] 等待服务启动...${NC}"
echo "正在等待后端服务健康检查..."

MAX_WAIT=120
WAIT_TIME=0
while [ $WAIT_TIME -lt $MAX_WAIT ]; do
    if docker compose ps | grep "data-platform-backend" | grep -q "healthy"; then
        echo -e "${GREEN}✓ 后端服务已就绪${NC}"
        break
    fi
    echo -n "."
    sleep 2
    WAIT_TIME=$((WAIT_TIME + 2))
done

if [ $WAIT_TIME -ge $MAX_WAIT ]; then
    echo -e "${RED}错误：服务启动超时${NC}"
    echo "请查看日志：docker compose logs backend"
    exit 1
fi

echo ""
echo -e "${GREEN}======================================"
echo "  部署完成！"
echo "======================================${NC}"
echo ""
echo "访问地址："
echo "  - 前端：http://$(curl -s ifconfig.me)/"
echo "  - API 文档：http://$(curl -s ifconfig.me)/swagger-ui.html"
echo "  - 健康检查：http://$(curl -s ifconfig.me)/actuator/health"
echo ""
echo "常用命令："
echo "  - 查看日志：docker compose logs -f"
echo "  - 查看状态：docker compose ps"
echo "  - 停止服务：docker compose stop"
echo "  - 重启服务：docker compose restart"
echo ""
