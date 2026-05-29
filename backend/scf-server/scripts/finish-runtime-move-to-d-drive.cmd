@echo off
setlocal
echo === SCF runtime move to D: (requires Administrator) ===

net stop postgresql-x64-16 >nul 2>&1
timeout /t 3 /nobreak >nul

sc config postgresql-x64-16 binPath= "\"D:\Program Files\PostgreSQL\16\bin\pg_ctl.exe\" runservice -N \"postgresql-x64-16\" -D \"D:\Program Files\PostgreSQL\16\data\" -w"
if errorlevel 1 (
  echo sc config failed
  exit /b 1
)

net start postgresql-x64-16
if errorlevel 1 (
  echo net start failed
  exit /b 1
)

setx JAVA_HOME "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" /M >nul
setx JAVA_HOME "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" >nul

if exist "C:\Program Files\Eclipse Adoptium" (
  rmdir /s /q "C:\Program Files\Eclipse Adoptium"
)

"D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" -version
set PGPASSWORD=postgres
"D:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d scf -c "SELECT 1 AS ok;"
set PGPASSWORD=

echo === DONE ===
pause
