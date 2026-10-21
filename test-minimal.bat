@echo off
chcp 65001 >nul
echo ======================================
echo   测试最小配置启动
echo ======================================
echo.
echo 当前配置：
echo - 已禁用 Security
echo - 已禁用 MyBatis-Plus 自定义配置
echo - 使用最小化配置
echo.
echo 如果此配置能启动成功，说明问题在于被禁用的配置
echo 如果仍然失败，说明问题在其他地方
echo.
pause
echo.
echo 正在启动...
echo.

cd /d D:\alert4
java -jar target\data-platform-1.0.0.jar --spring.profiles.active=minimal

pause
