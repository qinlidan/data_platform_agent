@echo off
chcp 65001 >nul
echo ========================================
echo   Java 17 安装验证工具
echo ========================================
echo.

echo [1] 检查 Java 版本...
java -version
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Java 未正确安装或未配置到 PATH
    goto :end
)
echo.

echo [2] 检查 Java 编译器版本...
javac -version
if %ERRORLEVEL% NEQ 0 (
    echo ❌ javac 未找到，请确认安装的是 JDK 而非 JRE
    goto :end
)
echo.

echo [3] 检查 JAVA_HOME 环境变量...
if "%JAVA_HOME%"=="" (
    echo ❌ JAVA_HOME 未设置
    echo 请设置 JAVA_HOME 环境变量
    goto :end
) else (
    echo ✅ JAVA_HOME = %JAVA_HOME%
)
echo.

echo [4] 检查 Maven 版本...
mvn -version
if %ERRORLEVEL% NEQ 0 (
    echo ⚠️  Maven 未找到，但不影响使用 JAR 启动
) else (
    echo ✅ Maven 已安装
)
echo.

echo [5] 验证 Java 版本是否为 17...
java -version 2>&1 | findstr /C:"version \"17" >nul
if %ERRORLEVEL% EQU 0 (
    echo ✅ Java 17 验证成功！
    echo.
    echo ========================================
    echo   🎉 环境配置正确！可以开始编译项目
    echo ========================================
    echo.
    echo 下一步操作：
    echo 1. 运行 rebuild.bat 重新编译项目
    echo 2. 或手动执行：mvn clean package -DskipTests
    echo.
) else (
    echo ❌ 当前 Java 版本不是 17
    echo 请检查环境变量配置或重新安装 Java 17
)

:end
echo.
pause
