@echo off
rem Start SpringBoot backend if port 8080 is not listening (used by logon task)
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if %errorlevel%==0 exit /b 0
cd /d "%~dp0backend"
start "" /b "E:\Java\bin\java.exe" -Dfile.encoding=UTF-8 -jar target\library-seat.jar
exit /b 0