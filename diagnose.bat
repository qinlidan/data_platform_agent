@echo off
chcp 65001 >nul
echo ======================================
echo   项目启动诊断工具
echo ======================================
echo.

echo [1/5] 检查Java版本...
java -version
echo.

echo [2/5] 检查Maven版本...
mvn -version
echo.

echo [3/5] 检查项目编译状态...
cd /d D:\alert4
if exist "target\data-platform-1.0.0.jar" (
    echo ✓ JAR文件已生成: target\data-platform-1.0.0.jar
) else (
    echo ✗ JAR文件不存在，需要重新编译
)
echo.

echo [4/5] 检查配置文件...
if exist "src\main\resources\application.yml" (
    echo ✓ 找到配置文件: application.yml
) else (
    echo ✗ 配置文件不存在
)
echo.

echo [5/5] 尝试启动应用（按Ctrl+C可以随时停止）...
echo.
echo 如果看到以下信息说明启动成功：
echo   "Started DataPlatformApplication in X.XXX seconds"
echo.
echo 如果启动失败，请仔细查看错误信息
echo ======================================
echo.
pause
echo 正在启动...
echo.

java -jar target\data-platform-1.0.0.jar
