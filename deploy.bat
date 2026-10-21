@echo off
REM 数据开发平台 - Windows 部署脚本

echo =========================================
echo   数据开发平台 Docker Compose 部署
echo =========================================

REM 检查 Docker
where docker >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未安装 Docker，请先安装 Docker Desktop
    pause
    exit /b 1
)

REM 检查 .env 文件
if not exist .env (
    echo 警告: 未找到 .env 文件，复制 .env.example...
    copy .env.example .env
    echo 请编辑 .env 文件，配置您的环境变量后再运行此脚本
    pause
    exit /b 1
)

echo.
echo 步骤 1/4: 清理旧容器和镜像...
docker-compose down --remove-orphans

echo.
echo 步骤 2/4: 构建 Docker 镜像...
docker-compose build --no-cache

echo.
echo 步骤 3/4: 启动服务...
docker-compose up -d

echo.
echo 步骤 4/4: 等待服务就绪...
timeout /t 15 /nobreak >nul

echo.
echo =========================================
echo   部署完成！
echo =========================================
echo.
echo 服务地址：
echo   前端: http://localhost:80
echo   后端: http://localhost:8089
echo   API 文档: http://localhost:8089/swagger-ui.html
echo   数据库: localhost:3306
echo.
echo 默认账户：
echo   用户名: admin
echo   密码: admin123
echo.
echo 查看日志：
echo   docker-compose logs -f [服务名]
echo.
echo 服务名称: frontend, backend, mysql
echo =========================================
echo.
pause
