Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "   CloudShield - Compiling & Starting Server...                  " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. Compile Java sources
Write-Host "[1/2] Compiling Java classes with javac..." -ForegroundColor Yellow
$sources = (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
javac -cp "lib/postgresql.jar;lib/servlet-api.jar" -d bin $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Java compilation failed." -ForegroundColor Red
    exit 1
}

Write-Host "[2/2] Launching Java HTTP Server on http://localhost:8080..." -ForegroundColor Green
Write-Host ">>> Demo Accounts:" -ForegroundColor Magenta
Write-Host "    Admin:    admin / admin123" -ForegroundColor White
Write-Host "    Operator: john  / john123" -ForegroundColor White
Write-Host "    Viewer:   alex  / alex123" -ForegroundColor White
Write-Host "------------------------------------------------------------------" -ForegroundColor Cyan

# Automatically open default browser after a brief delay
Start-Job -ScriptBlock {
    Start-Sleep -Seconds 2
    Start-Process "http://localhost:8080"
} | Out-Null

# 2. Run Java application
java -cp "bin;lib/postgresql.jar;lib/servlet-api.jar" CloudShield.Main
