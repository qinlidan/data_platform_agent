@echo off
chcp 65001 >nul
echo ======================================
echo   数据平台应用启动脚本
echo ======================================
echo.

REM 检查Java环境
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Java环境，请先安装Java 17或更高版本
    pause
    exit /b 1
)

echo Java版本信息:
java -version
echo.

REM 提示信息
echo 提示: 请确保已完成以下步骤：
echo   1. MySQL数据库已启动
echo   2. 已执行 src/main/resources/sql/schema.sql 初始化脚本
echo   3. 已在 application.yml 中配置正确的数据库连接信息
echo.

set /p continue="是否继续启动应用? (y/n): "
if /i not "%continue%"=="y" (
    echo 已取消启动
    pause
    exit /b 0
)

echo.
echo 正在启动应用...
echo ======================================
echo.

REM 启动应用
mvn spring-boot:run

pause
