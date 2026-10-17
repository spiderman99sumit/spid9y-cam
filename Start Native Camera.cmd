@echo off
if not exist "%~dp0Start-Native.ps1" (
  echo Keep the entire Spid9y Cam folder together. Use Create Desktop Shortcuts for Desktop access.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Native.ps1"
pause
