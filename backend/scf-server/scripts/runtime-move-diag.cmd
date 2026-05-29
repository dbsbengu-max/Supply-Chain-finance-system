@echo off
set LOG=e:\cursor\BR PT\供应链金融管理系统\backend\scf-server\scripts\runtime-move-diag.log
echo === %DATE% %TIME% === > "%LOG%"

sc qc postgresql-x64-16 >> "%LOG%" 2>&1
echo. >> "%LOG%"

net start postgresql-x64-16 >> "%LOG%" 2>&1
echo net start exit: %ERRORLEVEL% >> "%LOG%"
timeout /t 5 /nobreak >nul
sc query postgresql-x64-16 >> "%LOG%" 2>&1
echo. >> "%LOG%"

"D:\Program Files\PostgreSQL\16\bin\pg_ctl.exe" -D "D:\Program Files\PostgreSQL\16\data" start >> "%LOG%" 2>&1
echo pg_ctl start exit: %ERRORLEVEL% >> "%LOG%"
timeout /t 5 /nobreak >nul
sc query postgresql-x64-16 >> "%LOG%" 2>&1
echo. >> "%LOG%"

setx JAVA_HOME "D:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" /M >> "%LOG%" 2>&1
if exist "C:\Program Files\Eclipse Adoptium" rmdir /s /q "C:\Program Files\Eclipse Adoptium" >> "%LOG%" 2>&1

set PGPASSWORD=postgres
"D:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d scf -c "SELECT 1 AS ok;" >> "%LOG%" 2>&1
echo psql exit: %ERRORLEVEL% >> "%LOG%"

echo DONE >> "%LOG%"
