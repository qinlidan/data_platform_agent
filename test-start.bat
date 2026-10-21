@echo off
chcp 65001 >nul
echo ======================================
echo   测试应用启动
echo ======================================
echo.
echo 正在启动应用（测试模式）...
echo 如果出现错误，请按 Ctrl+C 停止
echo.

cd /d D:\alert4
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=test"
