@echo off
setlocal enabledelayedexpansion
title Library Seat System
cd /d "%~dp0"

echo ============================================================
echo   Library Seat Reservation System
echo   SpringBoot + Vue3 + MyBatis-Plus + Element Plus + MySQL
echo ============================================================
echo.

rem ---------- 1. check MySQL ----------
echo [1/4] Check MySQL service ...
sc query MySQL8 | findstr /I "RUNNING" >nul
if errorlevel 1 (
    net start MySQL8 >nul 2>&1
    if errorlevel 1 (
        echo       [WARN] Cannot start MySQL8, please start it manually
    ) else (
        echo       MySQL8 started
    )
) else (
    echo       MySQL8 is running
)

rem ---------- 2. optional database init: start.bat init ----------
echo [2/4] Prepare database ...
if /I "%1"=="init" (
    echo       Importing demo data ...
    mysql -uroot -p123456 < "sql\library_seat.sql" 2>nul
    mysql -uroot -p123456 < "sql\demo_history.sql" 2>nul
    echo       Demo data imported
) else (
    echo       Use existing data ^(reset demo data: start.bat init^)
)

rem ---------- 3. check jar ----------
echo [3/4] Prepare application ...
set "APP_JAR=%~dp0backend\target\library-seat.jar"
if not exist "%APP_JAR%" (
    echo       Jar not found, building now ^(first run takes 1-2 minutes^) ...
    call mvn -f "backend\pom.xml" clean package -DskipTests
    if errorlevel 1 (
        echo       [ERROR] Build failed, please check JDK / Maven / network
        pause
        exit /b 1
    )
)

rem ---------- 4. start app and open login page ----------
echo [4/4] Start system ...
echo.
echo       The browser will open the LOGIN page automatically.
echo       URL     : http://localhost:8080/login
echo       Admin   : admin / 123456
echo       Student : 20230001 / 123456
echo.

start /b "" java -Dfile.encoding=UTF-8 -jar "%APP_JAR%"

rem wait until the port is ready (max 45s), then open the login page
echo       Waiting for the server ...
set "READY="
for /l %%i in (1,1,45) do (
    powershell -NoProfile -Command "$c=New-Object Net.Sockets.TcpClient; try{$c.Connect('127.0.0.1',8080);$c.Close();exit 0}catch{exit 1}" >nul 2>&1
    if not errorlevel 1 (
        set "READY=1"
        goto :ready
    )
    ping -n 2 127.0.0.1 >nul
)

:ready
if not defined READY echo       [WARN] Server may still be starting, please refresh later.

start "" "http://localhost:8080/login"
echo.
echo       ========================================================
echo        Browser opened: http://localhost:8080/login
echo        Every start goes to the LOGIN page, not the last page.
echo        Close this window to keep the system running.
echo        To stop the system, run: stop.bat
echo       ========================================================
echo.
pause >nul