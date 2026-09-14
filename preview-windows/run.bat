@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo [ERROR] Virtual environment not found at .venv
    echo         Run setup.bat once, or follow docs/WINDOWS_PREVIEW.md.
    pause
    exit /b 1
)

".venv\Scripts\python.exe" hand_preview.py %*
if errorlevel 1 pause
