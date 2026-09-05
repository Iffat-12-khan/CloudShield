@echo off
echo ==================================================================
echo    CloudShield - Compiling and Starting Application Server
echo ==================================================================

echo [1/2] Compiling Java classes...
powershell -Command "javac -cp 'lib/postgresql.jar;lib/servlet-api.jar' -d bin (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/2] Launching CloudShield on http://localhost:8080...
start http://localhost:8080
java -cp "bin;lib/postgresql.jar;lib/servlet-api.jar" CloudShield.Main
pause
