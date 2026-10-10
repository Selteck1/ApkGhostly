@echo off
setlocal
title Kemtiz PC Server

set "ROOT=%~dp0"
set "BACKEND=%ROOT%kemtiz"
set "VENV=%BACKEND%\.venv"
set "DATA=%ROOT%kemtiz-data"
set "PYTHONUTF8=1"
set "PYTHONUNBUFFERED=1"
set "KEMTIZ_DATA_DIR=%DATA%"
set "KEMTIZ_DB_PATH=%DATA%\kemtiz.sqlite3"

if not exist "%DATA%" mkdir "%DATA%"
if not exist "%BACKEND%\server.py" (
  echo [ERROR] Cannot find kemtiz\server.py. Extract the whole repository or PC server package first.
  pause
  exit /b 1
)

if not exist "%VENV%\Scripts\python.exe" (
  echo [1/3] Creating Python virtual environment...
  where py >nul 2>nul
  if not errorlevel 1 (
    py -3 -m venv "%VENV%"
  ) else (
    python -m venv "%VENV%"
  )
  if errorlevel 1 (
    echo [ERROR] Python 3 was not found. Install Python 3.12+ from https://www.python.org/downloads/windows/
    pause
    exit /b 1
  )
)

echo [2/3] Checking backend dependencies...
"%VENV%\Scripts\python.exe" -m pip install -r "%BACKEND%\requirements.txt"
if errorlevel 1 (
  echo [ERROR] Could not install dependencies. Check internet access and try again.
  pause
  exit /b 1
)

cd /d "%BACKEND%"
echo.
echo [3/3] Kemtiz API is starting on port 8000.
echo Leave this window open while you use the messenger.
echo Local health check: http://127.0.0.1:8000/health
echo Database and token key are kept in: %DATA%
echo.
"%VENV%\Scripts\python.exe" -m uvicorn server:app --host 0.0.0.0 --port 8000
echo.
echo Kemtiz API stopped. Scroll up to see any error.
pause
