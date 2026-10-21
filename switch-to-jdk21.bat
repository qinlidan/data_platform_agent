@echo off
chcp 65001 >nul
echo ========================================
echo   切换到 JDK 21
echo ========================================
echo.

echo 检测到的 JDK 21 路径：
echo D:\jdk-21.0.12
echo.

echo [步骤1] 设置 JAVA_HOME（当前会话）...
set JAVA_HOME=D:\jdk-21.0.12
echo ✅ JAVA_HOME = %JAVA_HOME%
echo.

echo [步骤2] 设置 PATH（当前会话）...
set PATH=%JAVA_HOME%\bin;%PATH%
echo ✅ PATH 已更新
echo.

echo [步骤3] 验证 Java 版本...
java -version
echo.

echo [步骤4] 设置永久环境变量（需要管理员权限）...
echo 正在设置系统环境变量...
setx JAVA_HOME "D:\jdk-21.0.12" /M
if %ERRORLEVEL% EQU 0 (
    echo ✅ JAVA_HOME 已永久设置
) else (
    echo ⚠️  需要管理员权限设置系统环境变量
    echo 请右键此脚本选择"以管理员身份运行"
    echo 或手动设置环境变量
)
echo.

echo ========================================
echo   环境变量设置完成！
echo ========================================
echo.
echo 当前窗口已生效，可以直接使用
echo.
echo 下一步：
echo 1. 在当前窗口运行：rebuild.bat
echo 2. 或直接运行：mvn clean package -DskipTests
echo.
pause
