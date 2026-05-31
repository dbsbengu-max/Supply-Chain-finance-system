# Shared psql helper — local psql, or docker compose postgres container
# Dot-source: . "$PSScriptRoot\_psql.ps1"

function Get-ScfDbConfig {
    param([string]$PilotRoot)
    if (-not $PilotRoot) {
        $PilotRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
    }
    $envFile = Join-Path $PilotRoot ".env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
            $k, $v = $_ -split '=', 2
            if ($k -and $null -ne $v) { Set-Item -Path "env:$k" -Value $v.Trim() }
        }
    }
    @{
        Host = if ($env:SCF_DB_HOST) { $env:SCF_DB_HOST } else { "localhost" }
        Port = if ($env:SCF_DB_PORT) { $env:SCF_DB_PORT } else { "5432" }
        Db   = if ($env:SCF_DB_NAME) { $env:SCF_DB_NAME } else { "scf" }
        User = if ($env:SCF_DB_USER) { $env:SCF_DB_USER } else { "scf" }
        Pass = $env:SCF_DB_PASSWORD
    }
}

function Invoke-ScfPsql {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string]$PilotRoot,
        [string]$LogAppendPath
    )
    $cfg = Get-ScfDbConfig -PilotRoot $PilotRoot
    if (-not $cfg.Pass) { throw "SCF_DB_PASSWORD not set" }

    if (Get-Command psql -ErrorAction SilentlyContinue) {
        $env:PGPASSWORD = $cfg.Pass
        $output = & psql -h $cfg.Host -p $cfg.Port -U $cfg.User -d $cfg.Db -f $FilePath 2>&1
        $code = $LASTEXITCODE
        if ($LogAppendPath) {
            $output | Tee-Object -FilePath $LogAppendPath -Append | Out-Null
        } else {
            $output | ForEach-Object { Write-Host $_ }
        }
        return $code
    }

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    $container = if ($env:SCF_DOCKER_PG_CONTAINER) { $env:SCF_DOCKER_PG_CONTAINER } else { "scf-postgres" }
    if ($docker) {
        $running = docker ps --filter "name=$container" --format "{{.Names}}" 2>$null
        if ($running -eq $container) {
            $sql = Get-Content -Raw -Path $FilePath
            $out = $sql | docker exec -i -e PGPASSWORD=$cfg.Pass $container `
                psql -h localhost -U $cfg.User -d $cfg.Db 2>&1
            $code = $LASTEXITCODE
            if ($LogAppendPath) {
                $out | Tee-Object -FilePath $LogAppendPath -Append | Out-Null
            } else {
                $out | ForEach-Object { Write-Host $_ }
            }
            return $code
        }
    }

    throw "Neither psql nor docker container '$container' available. Install PostgreSQL client or start docker compose postgres."
}

function Invoke-ScfPsqlQuery {
    param(
        [Parameter(Mandatory)][string]$Query,
        [string]$PilotRoot
    )
    $cfg = Get-ScfDbConfig -PilotRoot $PilotRoot
    if (-not $cfg.Pass) { throw "SCF_DB_PASSWORD not set" }

    if (Get-Command psql -ErrorAction SilentlyContinue) {
        $env:PGPASSWORD = $cfg.Pass
        return (psql -h $cfg.Host -p $cfg.Port -U $cfg.User -d $cfg.Db -t -A -c $Query)
    }

    $container = if ($env:SCF_DOCKER_PG_CONTAINER) { $env:SCF_DOCKER_PG_CONTAINER } else { "scf-postgres" }
    if ((Get-Command docker -ErrorAction SilentlyContinue) -and (docker ps --filter "name=$container" -q)) {
        return (docker exec -e PGPASSWORD=$cfg.Pass $container psql -h localhost -U $cfg.User -d $cfg.Db -t -A -c $Query)
    }
    throw "psql unavailable"
}
