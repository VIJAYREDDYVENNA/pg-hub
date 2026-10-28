@echo off
setlocal
rem Starts the Spring Boot backend on http://localhost:8080
rem Credentials are read from backend\.env (never stored in this script).
cd /d "%~dp0..\backend"
if not exist ".env" (
  echo [ERROR] backend\.env not found. Copy backend\.env.example to backend\.env and fill in DB_PASSWORD and JWT_SECRET.
  exit /b 1
)
where mvn >nul 2>nul || (echo [ERROR] Maven ^(mvn^) is not on PATH. & exit /b 1)
where java >nul 2>nul || (echo [ERROR] Java 21+ is not on PATH. & exit /b 1)
title HostelHub Backend (port 8080)
echo Starting backend on http://localhost:8080  (Swagger: http://localhost:8080/swagger-ui.html)
call mvn -B spring-boot:run
