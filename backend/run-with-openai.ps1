$ErrorActionPreference = 'Stop'
$occupiedPort = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($occupiedPort) {
    throw 'Port 8080 is already in use. Stop the existing CareerPilot API before starting another instance; the local H2 database supports one embedded API process.'
}

$env:CAREERPILOT_AI_PROVIDER = 'openai'
Set-Location $PSScriptRoot
mvn spring-boot:run
