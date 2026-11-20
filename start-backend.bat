@echo off
rem ------------------------------------------------------------
rem Start SpringBoot backend (used by the boot-time scheduled task)
rem Retries up to 3 times because MySQL may start slower than this task.
rem NOTE: uses "ping" for delay instead of "timeout", because
rem       "timeout" fails when there is no console (scheduled task).
rem ------------------------------------------------------------
setlocal enabledelayedexpansion
set ATTEMPT=1

:loop
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if !errorlevel!==0 exit /b 0
if !ATTEMPT! GTR 3 exit /b 0

cd /d "%~dp0backend"
start "" /b "E:\Java\bin\java.exe" -Dfile.encoding=UTF-8 -jar target\library-seat.jar

ping -n 31 127.0.0.1 >nul

netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if !errorlevel!==0 exit /b 0

set /a ATTEMPT+=1
goto loop