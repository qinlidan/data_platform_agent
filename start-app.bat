@echo off
chcp 65001 >nul
echo ========================================
echo   启动 Data Platform 应用
echo ========================================
echo.

echo 配置信息：
echo - 端口：8080
echo - 数据库：MySQL (localhost:3306/data_platform)
echo - 用户名：root
echo - 密码：fra2781@
echo.

echo 检查 JAR 文件是否存在...
if not exist "D:\alert4\target\data-platform-1.0.0.jar" (
    echo ❌ 错误：未找到 JAR 文件
    echo 请先运行 rebuild.bat 编译项目
    echo.
    pause
    exit /b 1
)
echo ✅ JAR 文件已找到
echo.

echo 正在启动应用...
echo.
echo ----------------------------------------
echo   应用日志：
echo ----------------------------------------
echo.

cd /d D:\alert4
java -jar target\data-platform-1.0.0.jar

echo.
echo ----------------------------------------
echo 应用已停止
pause
