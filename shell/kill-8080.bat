@echo off
setlocal enabledelayedexpansion

for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
  set pid=%%a
)

if defined pid (
  echo Killing PID: %pid%
  taskkill /PID %pid% /F
) else (
  echo No process is listening on port 8080.
)

pause
