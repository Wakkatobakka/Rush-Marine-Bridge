@echo off
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
  py -3 build_payload.py
) else (
  python build_payload.py
)
echo.
pause
