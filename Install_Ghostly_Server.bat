
@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install_Ghostly_Server.ps1"
if errorlevel 1 (
  echo.
  echo Installation failed. See the error above.
)
echo.
pause
