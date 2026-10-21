#!/bin/bash

echo "======================================"
echo "  数据平台应用启动脚本"
echo "======================================"
echo ""

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装Java 17或更高版本"
    exit 1
fi

echo "Java版本信息:"
java -version
echo ""

# 检查MySQL连接
echo "提示: 请确保已完成以下步骤："
echo "  1. MySQL数据库已启动"
echo "  2. 已执行 src/main/resources/sql/schema.sql 初始化脚本"
echo "  3. 已在 application.yml 中配置正确的数据库连接信息"
echo ""

read -p "是否继续启动应用? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "已取消启动"
    exit 0
fi

echo ""
echo "正在启动应用..."
echo "======================================"
echo ""

# 启动应用
mvn spring-boot:run
