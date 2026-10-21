#!/bin/bash

# 数据开发平台 - 部署脚本

set -e

echo "========================================="
echo "  数据开发平台 Docker Compose 部署"
echo "========================================="

# 检查 Docker 和 Docker Compose
if ! command -v docker &> /dev/null; then
    echo "错误: 未安装 Docker，请先安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "错误: 未安装 Docker Compose，请先安装 Docker Compose"
    exit 1
fi

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "警告: 未找到 .env 文件，复制 .env.example..."
    cp .env.example .env
    echo "请编辑 .env 文件，配置您的环境变量后再运行此脚本"
    exit 1
fi

# 加载环境变量
source .env

echo ""
echo "步骤 1/4: 清理旧容器和镜像..."
docker-compose down --remove-orphans

echo ""
echo "步骤 2/4: 构建 Docker 镜像..."
docker-compose build --no-cache

echo ""
echo "步骤 3/4: 启动服务..."
docker-compose up -d

echo ""
echo "步骤 4/4: 等待服务就绪..."
echo "等待 MySQL 启动..."
sleep 10

echo "等待后端启动..."
for i in {1..30}; do
    if docker-compose exec -T backend wget -q --spider http://localhost:8089/actuator/health 2>/dev/null; then
        echo "✓ 后端服务已就绪"
        break
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "等待前端启动..."
for i in {1..15}; do
    if docker-compose exec -T frontend wget -q --spider http://localhost/health 2>/dev/null; then
        echo "✓ 前端服务已就绪"
        break
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="
echo ""
echo "服务地址："
echo "  前端: http://localhost:${FRONTEND_PORT:-80}"
echo "  后端: http://localhost:${BACKEND_PORT:-8089}"
echo "  API 文档: http://localhost:${BACKEND_PORT:-8089}/swagger-ui.html"
echo "  数据库: localhost:${MYSQL_PORT:-3306}"
echo ""
echo "默认账户："
echo "  用户名: admin"
echo "  密码: admin123"
echo ""
echo "查看日志："
echo "  docker-compose logs -f [服务名]"
echo ""
echo "服务名称: frontend, backend, mysql"
echo "========================================="
