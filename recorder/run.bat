@echo off
setlocal
cd /d "%~dp0"

set "HTTP_PROXY="
set "HTTPS_PROXY="
set "ALL_PROXY="
set "GIT_HTTP_PROXY="
set "GIT_HTTPS_PROXY="

if not exist ".venv\Scripts\python.exe" (
    echo [ERROR] No virtual environment yet. Run setup.bat once, then try again.
    pause
    exit /b 1
)

.venv\Scripts\python.exe record_references.py %*
