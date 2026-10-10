@echo off
setlocal
title Kemtiz - Start API and HTTPS

set "ROOT=%~dp0"
echo.
echo Starting Kemtiz API and Tailscale HTTPS...
echo Keep both opened windows running while friends use the messenger.
echo.

start "Kemtiz API" cmd.exe /k ""%ROOT%start_kemtiz_server.bat""
timeout /t 3 /nobreak >nul
start "Kemtiz HTTPS" cmd.exe /k ""%ROOT%start_kemtiz_public.bat""

echo Both windows were opened.
echo If the Tailscale URL stays the same, the phone app remembers it automatically.
echo You can close this small launcher window.
timeout /t 5 /nobreak >nul
