param(
    [switch]$Volumes
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$EnvFile = Join-Path $Root ".env"
$ComposeFile = Join-Path $Root "docker-compose.yml"

$args = @("compose", "--env-file", $EnvFile, "-f", $ComposeFile, "down")
if ($Volumes) { $args += "-v" }

& docker @args
exit $LASTEXITCODE
