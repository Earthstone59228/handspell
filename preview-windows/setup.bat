@echo off
setlocal
cd /d "%~dp0"

rem Proxy environment variables on some machines break uv's downloads, so clear
rem them for this window.
set "HTTP_PROXY="
set "HTTPS_PROXY="
set "ALL_PROXY="
set "GIT_HTTP_PROXY="
set "GIT_HTTPS_PROXY="

where uv >nul 2>nul
if errorlevel 1 (
    echo [ERROR] uv not found. Install it from https://docs.astral.sh/uv/ first.
    pause
    exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
    echo [1/3] Installing Python 3.12...
    uv python install 3.12
    if errorlevel 1 goto :fail
    echo [2/3] Creating virtual environment in preview-windows\.venv...
    uv venv .venv --python 3.12
    if errorlevel 1 goto :fail
)

echo [3/3] Installing Python dependencies...
uv pip install --python .venv\Scripts\python.exe -r requirements.txt
if errorlevel 1 goto :fail

echo.
echo Setup complete. Double-click run.bat to start the preview.
pause
exit /b 0

:fail
echo.
echo Setup failed. See the messages above.
pause
exit /b 1
