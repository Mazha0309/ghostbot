@echo off
chcp 65001 >nul

:: 自动设置 Maven 项目版本的脚本 (Windows)
:: 使用方法: build.bat [额外参数]
:: 示例: build.bat -DskipTests

set BASE_VERSION=1.0.0

:: 获取当前日期时间
for /f "tokens=2 delims==" %%a in ('wmic os get localdatetime /value') do set DATETIME=%%a
set VERSION=%BASE_VERSION%-%DATETIME:~0,8%.%DATETIME:~8,6%

echo ========================================
echo   Bot Plugin Builder
echo ========================================
echo.
echo 版本号: %VERSION%
echo 编译时间: %date% %time%
echo.

echo 开始构建...
echo ========================================
mvn clean package -Drevision="%VERSION%" %*

if %ERRORLEVEL% == 0 (
    echo.
    echo ========================================
    echo ✅ 构建成功!
    echo 版本: %VERSION%
    echo 📦 JAR 文件位置: target\bot-%VERSION%.jar
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ❌ 构建失败!
    echo ========================================
    exit /b 1
)
