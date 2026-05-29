@echo off
REM Run this file as Administrator (right-click -> Run as administrator)
setlocal EnableExtensions
set LOG=%~dp0runtime-move-final.log
echo [%DATE% %TIME%] Starting final runtime move steps > "%LOG%"

echo Stopping PostgreSQL if running...
net stop postgresql-x64-16 >> "%LOG%" 2>&1
timeout /t 2 /nobreak >nul

echo Configuring PostgreSQL service to D:...
sc config postgresql-x64-16 binPath= "\"D:\Program Files\PostgreSQL\16\bin\pg_ctl.exe\" runservice -N \"postgresql-x64-16\" -D \"D:\Program Files\PostgreSQL\16\data\" -w" >> "%LOG%" 2>&1
if errorlevel 1 goto :fail

echo Starting PostgreSQL...
net start postgresql-x64-16 >> "%LOG%" 2>&1
if errorlevel 1 goto :fail
timeout /t 4 /nobreak >nul

echo Setting JAVA_HOME to D:...
setx JAVA_HOME "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" /M >> "%LOG%" 2>&1
setx JAVA_HOME "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" >> "%LOG%" 2>&1

echo Removing old C: JDK copy...
if exist "C:\Program Files\Eclipse Adoptium" (
  rmdir /s /q "C:\Program Files\Eclipse Adoptium" >> "%LOG%" 2>&1
)

echo Verifying Java...
"D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" -version >> "%LOG%" 2>&1

echo Verifying PostgreSQL...
set PGPASSWORD=postgres
"D:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d scf -c "SELECT 1 AS ok;" >> "%LOG%" 2>&1
set PGPASSWORD=

echo SUCCESS >> "%LOG%"
echo.
echo Done. See log: %LOG%
type "%LOG%"
pause
exit /b 0

:fail
echo FAILED >> "%LOG%"
echo Failed. See log: %LOG%
type "%LOG%"
pause
exit /b 1
