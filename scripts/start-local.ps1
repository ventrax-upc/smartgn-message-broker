[CmdletBinding()]
param(
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repositoryRoot '.env'
$envTemplate = Join-Path $repositoryRoot '.env.example'

if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath $envTemplate -Destination $envFile
    Write-Host 'Created .env from .env.example. Update broker/API credentials before relying on external operations.'
}

Push-Location $repositoryRoot
try {
    $composeArgs = @('compose', 'up', '-d')
    if ($Build) { $composeArgs += '--build' }
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) { throw "docker $($composeArgs -join ' ') failed with exit code $LASTEXITCODE" }
    & (Join-Path $PSScriptRoot 'smoke-local.ps1')
} finally {
    Pop-Location
}
