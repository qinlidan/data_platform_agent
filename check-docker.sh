#!/bin/bash

# Docker 配置验证脚本

echo "========================================="
echo "  Docker 配置验证"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_command() {
    if command -v $1 &> /dev/null; then
        echo -e "${GREEN}✓${NC} $1 已安装: $(command -v $1)"
        return 0
    else
        echo -e "${RED}✗${NC} $1 未安装"
        return 1
    fi
}

check_file() {
    if [ -f $1 ]; then
        echo -e "${GREEN}✓${NC} 文件存在: $1"
        return 0
    else
        echo -e "${RED}✗${NC} 文件缺失: $1"
        return 1
    fi
}

check_dir() {
    if [ -d $1 ]; then
        echo -e "${GREEN}✓${NC} 目录存在: $1"
        return 0
    else
        echo -e "${RED}✗${NC} 目录缺失: $1"
        return 1
    fi
}

# 1. 检查必要的命令
echo "1. 检查必备工具"
echo "-------------------"
check_command docker
DOCKER_OK=$?
check_command docker-compose || check_command "docker compose"
COMPOSE_OK=$?
echo ""

# 2. 检查 Docker 版本
if [ $DOCKER_OK -eq 0 ]; then
    echo "2. Docker 版本信息"
    echo "-------------------"
    docker --version
    docker-compose --version 2>/dev/null || docker compose version
    echo ""
fi

# 3. 检查必要的文件
echo "3. 检查配置文件"
echo "-------------------"
check_file "Dockerfile"
check_file "docker-compose.yml"
check_file ".env.example"
check_file "frontend/Dockerfile"
check_file "frontend/nginx.conf"
check_file "deploy/mysql/init.sql"
check_file "deploy/mysql/conf.d/my.cnf"
echo ""

# 4. 检查目录结构
echo "4. 检查目录结构"
echo "-------------------"
check_dir "src"
check_dir "frontend"
check_dir "deploy/mysql"
check_dir "deploy/mysql/conf.d"
echo ""

# 5. 检查环境变量文件
echo "5. 检查环境配置"
echo "-------------------"
if [ -f .env ]; then
    echo -e "${GREEN}✓${NC} .env 文件存在"

    # 检查关键配置
    if grep -q "MYSQL_ROOT_PASSWORD=root123456" .env; then
        echo -e "${YELLOW}⚠${NC} 警告: 使用默认 MySQL 密码，生产环境请修改"
    fi

    if grep -q "JWT_SECRET=your-256-bit-secret-key" .env; then
        echo -e "${YELLOW}⚠${NC} 警告: 使用默认 JWT 密钥，生产环境请修改"
    fi
else
    echo -e "${YELLOW}⚠${NC} .env 文件不存在，请从 .env.example 复制"
fi
echo ""

# 6. 检查端口占用
echo "6. 检查端口占用"
echo "-------------------"
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1 || netstat -tuln 2>/dev/null | grep -q ":$1 "; then
        echo -e "${YELLOW}⚠${NC} 端口 $1 已被占用"
        return 1
    else
        echo -e "${GREEN}✓${NC} 端口 $1 可用"
        return 0
    fi
}

check_port 80
check_port 8089
check_port 3306
echo ""

# 7. Docker 配置检查
if [ $DOCKER_OK -eq 0 ]; then
    echo "7. Docker 配置"
    echo "-------------------"

    # 检查 Docker 是否运行
    if docker ps >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Docker daemon 正在运行"
    else
        echo -e "${RED}✗${NC} Docker daemon 未运行，请启动 Docker"
    fi

    # 检查 Docker 磁盘空间
    DISK_USAGE=$(docker system df -v 2>/dev/null | grep -i "Images\|Containers\|Local Volumes" || echo "无法获取")
    echo "Docker 资源使用情况:"
    echo "$DISK_USAGE"
    echo ""
fi

# 8. 验证 docker-compose.yml 语法
if [ $COMPOSE_OK -eq 0 ]; then
    echo "8. 验证 docker-compose.yml 语法"
    echo "-------------------"
    if docker-compose config >/dev/null 2>&1 || docker compose config >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} docker-compose.yml 语法正确"
    else
        echo -e "${RED}✗${NC} docker-compose.yml 语法错误"
        docker-compose config 2>&1 || docker compose config 2>&1
    fi
    echo ""
fi

# 9. 总结
echo "========================================="
echo "  验证总结"
echo "========================================="

if [ $DOCKER_OK -eq 0 ] && [ $COMPOSE_OK -eq 0 ]; then
    echo -e "${GREEN}✓${NC} 基础环境检查通过"
else
    echo -e "${RED}✗${NC} 基础环境不完整，请先安装 Docker 和 Docker Compose"
fi

echo ""
echo "下一步:"
if [ ! -f .env ]; then
    echo "1. 复制并编辑环境变量: cp .env.example .env"
fi
echo "2. 运行部署脚本: ./deploy.sh"
echo ""
