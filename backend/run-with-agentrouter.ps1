$ErrorActionPreference = 'Stop'

$envFile = Join-Path $PSScriptRoot '..\.env'
if (Test-Path -LiteralPath $envFile) {
    $keyLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*CODEX_GATEWAY_API_KEY\s*=' } | Select-Object -Last 1
    if ($keyLine) {
        $routerKey = ($keyLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
    }
    if ([string]::IsNullOrWhiteSpace($routerKey)) {
        $keyLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*AGENTROUTER_API_KEY\s*=' } | Select-Object -Last 1
    }
    if ($keyLine) {
        $routerKey = ($keyLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
    }
    $modelLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*OPENAI_MODEL\s*=' } | Select-Object -Last 1
    if ($modelLine) {
        $modelName = ($modelLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
    }
    $baseUrlLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*CAREERPILOT_AI_BASE_URL\s*=' } | Select-Object -Last 1
    if ($baseUrlLine) {
        $baseUrl = ($baseUrlLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
    }
}
if ([string]::IsNullOrWhiteSpace($routerKey)) { $routerKey = $env:CODEX_GATEWAY_API_KEY }
if ([string]::IsNullOrWhiteSpace($routerKey)) { $routerKey = $env:AGENTROUTER_API_KEY }
if ([string]::IsNullOrWhiteSpace($routerKey)) {
    throw 'Set CODEX_GATEWAY_API_KEY (or AGENTROUTER_API_KEY) in the project-root .env file before starting the AgentRouter backend.'
}
$keyLine = $null
$modelLine = $null
$baseUrlLine = $null
$envFileBaseUrl = if ([string]::IsNullOrWhiteSpace($baseUrl)) { 'https://agentrouter.org/v1' } else { $baseUrl }

$occupiedPort = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($occupiedPort) {
    throw 'Port 8080 is already in use. Stop the existing CareerPilot API before starting another instance; the local H2 database supports one embedded API process.'
}

$env:CAREERPILOT_AI_PROVIDER = 'agentrouter'
$env:CAREERPILOT_AI_BASE_URL = $envFileBaseUrl
$env:CODEX_GATEWAY_API_KEY = $routerKey
$env:AGENTROUTER_API_KEY = $routerKey
if (-not [string]::IsNullOrWhiteSpace($modelName)) { $env:OPENAI_MODEL = $modelName }
$routerKey = $null
$modelName = $null
$envFileBaseUrl = $null
Set-Location $PSScriptRoot
mvn spring-boot:run
