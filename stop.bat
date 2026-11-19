@echo off
setlocal enabledelayedexpansion
title Stop Library Seat System

echo ============================================================
echo   Stop Library Seat Reservation System
echo ============================================================
echo.

set "FOUND="

rem 1) stop every process whose command line contains library-seat.jar
for /f "tokens=1" %%p in ('powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" ^| Where-Object { $_.CommandLine -like '*library-seat.jar*' } ^| ForEach-Object { $_.ProcessId }"') do (
    set "FOUND=1"
    echo       Stopping PID %%p ...
    taskkill /F /PID %%p >nul 2>&1
)

rem 2) fallback: kill whatever is listening on port 8080 (only java processes)
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r ":8080 .*LISTENING"') do (
    set "ISJAVA="
    for /f "tokens=1" %%n in ('tasklist /FI "PID eq %%p" /NH 2^>nul') do (
        if /I "%%n"=="java.exe" set "ISJAVA=1"
    )
    if defined ISJAVA (
        set "FOUND=1"
        echo       Stopping PID %%p ^(port 8080^) ...
        taskkill /F /PID %%p >nul 2>&1
    )
)

if not defined FOUND (
    echo       No running system process found
) else (
    echo       System stopped
)

echo.
echo       8080 port status:
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul && echo       8080 is still in use by another program || echo       8080 released
echo.
pause