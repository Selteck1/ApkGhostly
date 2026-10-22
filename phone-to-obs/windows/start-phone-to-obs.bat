@echo off
setlocal
echo PhoneToOBS receiver setup
echo 1. Download MediaMTX for Windows from https://github.com/bluenviron/mediamtx/releases
echo 2. Put mediamtx.exe in this folder beside mediamtx.yml
echo 3. Connect phone over USB and enable USB debugging
echo 4. Run: adb reverse tcp:1935 tcp:1935
echo 5. Run this script to start the RTMP server
if not exist mediamtx.exe (
  echo mediamtx.exe not found. Download the Windows archive from the official release page.
  start "" "https://github.com/bluenviron/mediamtx/releases"
  pause
  exit /b 1
)
mediamtx.exe mediamtx.yml
