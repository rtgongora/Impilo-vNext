#!/usr/bin/env pwsh
<#
Build and test vito-service and varapi-service with proper output capture
#>

$mvnPath = 'C:\Program Files\apache-maven\apache-maven-3.9.14\bin\mvn.cmd'
$servicesDir = 'c:\Users\rgong\OneDrive\Documents\EHR\vNext\Repo\Impilo-vNext\services'

Set-Location $servicesDir

Write-Host "=== Maven Test Build ===" -ForegroundColor Cyan
Write-Host "Building: vito-service, varapi-service" -ForegroundColor Green
Write-Host "Command: $mvnPath -pl vito-service,varapi-service -am package" -ForegroundColor Yellow
Write-Host ""

# Run the build
$proc = & $mvnPath -pl vito-service,varapi-service -am package
Write-Output $proc

# Check exit status
if ($LASTEXITCODE -eq 0) {
    Write-Host "`n=== BUILD SUCCESS ===" -ForegroundColor Green
} else {
    Write-Host "`n=== BUILD FAILED ===" -ForegroundColor Red
    Write-Host "Exit Code: $LASTEXITCODE" -ForegroundColor Red
}

exit $LASTEXITCODE
