$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath
& 'C:\Program Files\apache-maven\apache-maven-3.9.14\bin\mvn.cmd' -pl varapi-service,vito-service -am package > broader-build.log 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Output 'BUILD_SUCCESS'
} else {
    Write-Output 'BUILD_FAIL'
}
