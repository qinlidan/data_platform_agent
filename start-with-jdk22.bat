@echo off
chcp 65001 >nul
echo ========================================
echo   切换到 JDK 22 并启动项目
echo ========================================
echo.

echo [1] 切换到 JDK 22...
set JAVA_HOME=C:\Program Files\Java\jdk-22
set PATH=%JAVA_HOME%\bin;%PATH%
echo ✅ JAVA_HOME = %JAVA_HOME%
echo.

echo [2] 验证 Java 版本...
java -version
echo.

echo [3] 启动应用（最小配置测试）...
echo.
echo ========================================
echo   应用日志：
echo ========================================
echo.
cd /d D:\alert4
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal

pause
