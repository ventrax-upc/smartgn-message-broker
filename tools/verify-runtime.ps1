[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8087',
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

function Get-LocalSetting([string]$Name, [string]$Default) {
    $processValue = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($processValue)) { return $processValue }

    $envFile = Join-Path $repositoryRoot '.env'
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match ('^\s*' + [regex]::Escape($Name) + '\s*=\s*(.*)\s*$')) {
                return $Matches[1].Trim().Trim('"').Trim("'")
            }
        }
    }
    return $Default
}

$healthUri = $BaseUrl.TrimEnd('/') + '/actuator/health'
$deadline = (Get-Date).AddSeconds($WaitSeconds)
$health = $null
do {
    try {
        $health = Invoke-RestMethod -Method Get -Uri $healthUri -TimeoutSec 5
        if ($health.status -eq 'UP') { break }
    } catch {
        Start-Sleep -Seconds 2
    }
} while ((Get-Date) -lt $deadline)
if ($null -eq $health -or $health.status -ne 'UP') {
    throw "Broker health did not report UP at $healthUri within $WaitSeconds seconds."
}
Write-Host "PASS Broker Actuator health: $($health.status)"

$contract = Invoke-RestMethod -Method Get -Uri ($BaseUrl.TrimEnd('/') + '/v3/api-docs') -TimeoutSec 10
$requiredPaths = @(
    '/api/v1/broker/messages',
    '/api/v1/broker/messages/publish',
    '/api/v1/broker/devices',
    '/api/v1/broker/devices/authorize',
    '/api/v1/broker/devices/{deviceId}/revoke'
)
foreach ($path in $requiredPaths) {
    if ($null -eq $contract.paths.PSObject.Properties[$path]) {
        throw "OpenAPI is missing required broker operation path '$path'."
    }
}
if ($null -eq $contract.components.securitySchemes.serviceAuth) {
    throw 'OpenAPI is missing the serviceAuth bearer scheme.'
}
Write-Host "PASS Broker OpenAPI: $($requiredPaths.Count) required routes and serviceAuth"

$serviceToken = Get-LocalSetting 'INTERNAL_SERVICE_TOKEN' ''
if ([string]::IsNullOrWhiteSpace($serviceToken) -or $serviceToken -like 'replace-*') {
    throw 'Set INTERNAL_SERVICE_TOKEN in the process environment or local .env before running this smoke check.'
}
$headers = @{ Authorization = "Bearer $serviceToken" }
$recentMessages = Invoke-RestMethod -Method Get `
    -Uri ($BaseUrl.TrimEnd('/') + '/api/v1/broker/messages') `
    -Headers $headers -TimeoutSec 10
Write-Host 'PASS Broker authenticated read-only recent-message endpoint'
