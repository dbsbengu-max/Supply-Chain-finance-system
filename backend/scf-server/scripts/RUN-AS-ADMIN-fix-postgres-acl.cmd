@echo off
setlocal
set LOG=%~dp0postgres-start-debug.log
echo === %DATE% %TIME% === > "%LOG%"

echo Fixing ACL for NetworkService...
icacls "D:\Program Files\PostgreSQL\16" /grant "NT AUTHORITY\NETWORK SERVICE:(OI)(CI)F" /T >> "%LOG%" 2>&1
icacls "D:\Program Files\PostgreSQL\16" /grant "BUILTIN\Users:(OI)(CI)RX" /T >> "%LOG%" 2>&1

echo pg_ctl status >> "%LOG%"
"D:\Program Files\PostgreSQL\16\bin\pg_ctl.exe" -D "D:\Program Files\PostgreSQL\16\data" status >> "%LOG%" 2>&1

echo pg_ctl start >> "%LOG%"
"D:\Program Files\PostgreSQL\16\bin\pg_ctl.exe" -D "D:\Program Files\PostgreSQL\16\data" -l "D:\Program Files\PostgreSQL\16\data\log\manual-start.log" start -w >> "%LOG%" 2>&1

echo net start >> "%LOG%"
net start postgresql-x64-16 >> "%LOG%" 2>&1
sc query postgresql-x64-16 >> "%LOG%" 2>&1

type "%LOG%"
pause
