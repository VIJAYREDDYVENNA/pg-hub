@echo off
setlocal
rem Starts backend and frontend in separate windows. MySQL must already be running.
set "ROOT=%~dp0"
echo Make sure MySQL is running on localhost:3306 before continuing.
start "HostelHub Backend" cmd /k ""%ROOT%start-backend.bat""
echo Waiting for the backend to become healthy...
powershell -NoProfile -Command "$ok=$false; for($i=0;$i -lt 90;$i++){ try { if((Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health -TimeoutSec 2).StatusCode -eq 200){$ok=$true;break} } catch {}; Start-Sleep 2 }; if(-not $ok){ Write-Host 'Backend did not report healthy yet - starting frontend anyway.' }"
start "HostelHub Frontend" cmd /k ""%ROOT%start-frontend.bat""
echo.
echo Frontend: http://localhost:3000
echo Backend : http://localhost:8080   Swagger: http://localhost:8080/swagger-ui.html
