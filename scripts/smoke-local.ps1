[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8087',
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = 'Stop'
& (Join-Path (Split-Path -Parent $PSScriptRoot) 'tools/verify-runtime.ps1') -BaseUrl $BaseUrl -WaitSeconds $WaitSeconds
