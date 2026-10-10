@echo off
setlocal
title Kemtiz Public HTTPS - Tailscale Funnel

set "TAILSCALE="
where tailscale.exe >nul 2>nul
if not errorlevel 1 set "TAILSCALE=tailscale.exe"
if not defined TAILSCALE if exist "%ProgramFiles%\Tailscale\tailscale.exe" set "TAILSCALE=%ProgramFiles%\Tailscale\tailscale.exe"
if not defined TAILSCALE if exist "%ProgramFiles(x86)%\Tailscale\tailscale.exe" set "TAILSCALE=%ProgramFiles(x86)%\Tailscale\tailscale.exe"

if not defined TAILSCALE (
  echo Tailscale is required for public HTTPS access.
  echo Installing it with Windows Package Manager...
  where winget >nul 2>nul
  if errorlevel 1 (
    echo Install Tailscale from https://tailscale.com/download/windows
    pause
    exit /b 1
  )
  winget install -e --id Tailscale.Tailscale
  echo.
  echo After installation, open Tailscale, sign in, and run this file again.
  pause
  exit /b 0
)

echo.
echo Kemtiz server should already be running in another window.
echo This publishes HTTPS access to the server for people outside your Wi-Fi.
echo Only the API is exposed; accounts still require login and password.
echo Keep this window open while friends use Kemtiz.
echo.
echo If a browser asks to approve Funnel, approve it. The URL will appear below.
echo Official guide: https://tailscale.com/docs/features/tailscale-funnel
echo.
"%TAILSCALE%" funnel 8000
echo.
echo Public connection stopped. Run this file again to publish it.
pause
