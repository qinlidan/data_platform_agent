@echo off
chcp 65001 >nul
echo ========================================
echo   重新编译 Data Platform 项目
echo ========================================
echo.

echo [检查] 验证 Java 版本...
java -version 2>&1 | findstr /C:"version \"17" >nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 错误：未检测到 Java 17
    echo 请先运行 check-java17.bat 验证 Java 安装
    echo.
    pause
    exit /b 1
)
echo ✅ Java 17 已就绪
echo.

echo [步骤1] 清理旧的编译文件...
cd /d D:\alert4
mvn clean
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 清理失败
    pause
    exit /b 1
)
echo ✅ 清理完成
echo.

echo [步骤2] 编译项目...
mvn compile
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 编译失败，请检查错误信息
    pause
    exit /b 1
)
echo ✅ 编译完成
echo.

echo [步骤3] 打包项目...
mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 打包失败
    pause
    exit /b 1
)
echo ✅ 打包完成
echo.

echo ========================================
echo   🎉 编译成功！
echo ========================================
echo.
echo JAR 文件位置：
echo   target\data-platform-1.0.0.jar
echo.
echo 下一步操作：
echo   1. 运行 start-app.bat 启动应用
echo   2. 或运行 test-minimal.bat 使用最小配置测试
echo.
pause
