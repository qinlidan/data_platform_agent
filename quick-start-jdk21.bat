@echo off
chcp 65001 >nul
echo ========================================
echo   使用 JDK 21 编译并启动项目
echo ========================================
echo.

echo [1] 切换到 JDK 21...
set JAVA_HOME=D:\jdk-21.0.12
set PATH=%JAVA_HOME%\bin;%PATH%
echo ✅ JAVA_HOME = %JAVA_HOME%
echo.

echo [2] 验证 Java 版本...
java -version
echo.

echo [3] 清理并重新编译项目...
cd /d D:\alert4
mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 编译失败
    pause
    exit /b 1
)
echo ✅ 编译成功
echo.

echo [4] 启动应用（最小配置测试）...
echo.
echo ========================================
echo   应用日志：
echo ========================================
echo.
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal

pause
