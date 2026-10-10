@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Lineup - Publish data

set "REPO_URL=https://github.com/Selteck1/ApkGhostly.git"
set "PACKAGE=%~dp0community.lineup"
set "WORK_DIR=%TEMP%\LineupPublisher_%RANDOM%_%RANDOM%"

echo.
echo ============================================
echo   LINEUP - Publish data to GitHub
echo ============================================
echo.

if not exist "%PACKAGE%" (
  echo ERROR: community.lineup was not found.
  echo Put community.lineup in the same folder as this BAT.
  goto :failed
)

where git >nul 2>nul
if errorlevel 1 (
  echo ERROR: Git for Windows is not installed or is not in PATH.
  echo Install it from https://git-scm.com/download/win
  goto :failed
)

where python >nul 2>nul
if errorlevel 1 (
  echo ERROR: Python 3 is not installed or is not in PATH.
  echo Install it from https://www.python.org/downloads/windows/
  echo During setup, enable "Add Python to PATH".
  goto :failed
)

echo [1/5] Downloading the latest repository state...
git clone --depth 1 --branch main "%REPO_URL%" "%WORK_DIR%"
if errorlevel 1 (
  echo ERROR: Could not clone the repository.
  echo Check your internet connection and GitHub access.
  goto :failed
)

echo [2/5] Copying the content package...
if not exist "%WORK_DIR%\content\packs" mkdir "%WORK_DIR%\content\packs"
copy /Y "%PACKAGE%" "%WORK_DIR%\content\packs\community.lineup" >nul
if errorlevel 1 (
  echo ERROR: Could not copy community.lineup.
  goto :failed
)

pushd "%WORK_DIR%"
if errorlevel 1 (
  echo ERROR: Could not open the temporary repository folder.
  goto :failed
)

echo [3/5] Rebuilding the catalog...
python scripts\build_catalog.py
if errorlevel 1 (
  echo ERROR: The package is invalid or the catalog could not be built.
  popd
  goto :failed
)

echo [4/5] Preparing the Git commit...
git config user.name "Lineup Publisher"
git config user.email "lineup@users.noreply.github.com"
git add content/packs/community.lineup content/catalog.json content/policy.json

git diff --cached --quiet
if not errorlevel 1 (
  echo.
  echo This package is already published. No changes are needed.
  popd
  rmdir /s /q "%WORK_DIR%" >nul 2>nul
  goto :success
)

git commit -m "Publish Lineup content"
if errorlevel 1 (
  echo ERROR: Git could not create the publication commit.
  popd
  goto :failed
)

echo [5/5] Uploading the update to GitHub...
git push origin HEAD:main
if errorlevel 1 (
  echo ERROR: GitHub rejected the upload.
  echo Sign in with a GitHub account that can write to Selteck1/ApkGhostly,
  echo then run this BAT again. If remote changes conflict, rerun it to clone
  echo the latest main branch into a fresh temporary folder.
  popd
  goto :failed
)

popd
rmdir /s /q "%WORK_DIR%" >nul 2>nul

:success
echo.
echo ============================================
echo   SUCCESS: Lineup data was published!
echo   Users will receive it during the next app refresh.
echo ============================================
echo.
pause
exit /b 0

:failed
echo.
echo Publication stopped. Read the error above.
echo The original community.lineup file was not changed.
echo.
pause
exit /b 1
