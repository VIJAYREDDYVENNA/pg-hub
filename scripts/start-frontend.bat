@echo off
setlocal
rem Starts the React frontend (Webpack dev server) on http://localhost:3000
cd /d "%~dp0..\frontend"
where yarn >nul 2>nul || (echo [ERROR] Yarn is not on PATH. Install with: npm install -g yarn & exit /b 1)
if not exist "node_modules" (
  echo Installing frontend dependencies with Yarn...
  call yarn install || exit /b 1
)
title HostelHub Frontend (port 3000)
echo Starting frontend on http://localhost:3000
call yarn start
