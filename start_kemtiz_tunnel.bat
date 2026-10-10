@echo off
setlocal
title Kemtiz Cloudflare Tunnel

where cloudflared >nul 2>nul
if errorlevel 1 (
  echo Cloudflare Tunnel utility cloudflared is not installed.
  where winget >nul 2>nul
  if errorlevel 1 (
    echo Install cloudflared using the official guide:
    echo https://developers.cloudflare.com/tunnel/downloads/
    pause
    exit /b 1
  )
  echo Installing cloudflared through Windows Package Manager...
  winget install -e --id Cloudflare.cloudflared
  echo.
  echo Installation command finished. Close this window and run this file again.
  pause
  exit /b 0
)

echo Starting public HTTPS tunnel to local Kemtiz API at http://127.0.0.1:8000
echo.
echo Copy the generated https://...trycloudflare.com URL into Kemtiz Messenger
echo under "SVOY SERVER", then press "Save and check server".
echo Keep this window open. Closing it disables the public connection.
echo.
cloudflared tunnel --url http://127.0.0.1:8000
echo.
echo Tunnel stopped. Run this file again to reconnect.
pause
