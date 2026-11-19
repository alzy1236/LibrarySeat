@echo off
chcp 65001 >nul
title 自习室座位预约管理系统 - 开发模式
echo ==========================================================
echo   开发模式：后端 8080 / 前端 5173（热更新）
echo ==========================================================
echo.

echo [1] 启动 SpringBoot 后端...
start "LibrarySeat-Backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"

echo [2] 等待后端就绪...
timeout /t 20 >nul

echo [3] 启动 Vue3 前端...
start "LibrarySeat-Frontend" cmd /k "cd /d %~dp0frontend && npm install && npm run dev"

echo.
echo 前端开发地址：http://localhost:5173
echo 后端接口地址：http://localhost:8080/api
echo 关闭弹出的两个命令行窗口即可停止服务。
pause