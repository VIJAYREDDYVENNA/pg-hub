@echo off
setlocal enabledelayedexpansion
rem Stops whatever is listening on the frontend (3000) and backend (8080) ports.
for %%P in (3000 8080) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr /r /c:":%%P .*LISTENING"') do (
    if not "%%A"=="0" (
      echo Stopping process %%A on port %%P
      taskkill /PID %%A /T /F >nul 2>nul
    )
  )
)
echo Done. MySQL was not touched.
