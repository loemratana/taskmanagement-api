<#
.SYNOPSIS
    Deploys a new app.jar to the Windows Server and rolls back if it does not become healthy.

.DESCRIPTION
    Run by the "deploy" job in .github/workflows/ci.yml on the self-hosted runner.
      1. Backs up the database with pg_dump (Flyway migrates on startup; a jar rollback does not undo that).
      2. Stops the service, keeps the current jar as app.jar.bak, copies the new jar in.
      3. Starts the service and polls /management/health/readiness (database + MinIO).
      4. If it is not healthy in time: restores app.jar.bak, restarts, and fails the job.

    Written for Windows PowerShell 5.1. Keep this file ASCII-only (5.1 misreads UTF-8 without a BOM).

.EXAMPLE
    .\deploy\windows\deploy.ps1 -JarPath dist\app.jar
#>
param(
    [Parameter(Mandatory = $true)] [string]$JarPath,
    [string]$AppDir      = 'E:\company\applications\task-management',
    [string]$ServiceName = 'tms-api',
    [int]$TimeoutSec     = 180,
    [int]$KeepBackups    = 10,
    [switch]$SkipDbBackup
)

$ErrorActionPreference = 'Stop'

$liveJar   = Join-Path $AppDir 'app.jar'
$backupJar = Join-Path $AppDir 'app.jar.bak'
$envFile   = Join-Path $AppDir '.env'

# ---------- helpers ----------

# Reads KEY=VALUE lines from the server's .env (the same file the app imports at startup).
function Read-EnvFile([string]$path) {
    $values = @{}
    if (-not (Test-Path $path)) { return $values }
    foreach ($line in Get-Content $path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key   = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim().Trim('"').Trim("'")
        $values[$key] = $value
    }
    return $values
}

function Get-Setting($envValues, [string]$key, [string]$default) {
    if ($envValues.ContainsKey($key) -and $envValues[$key] -ne '') { return $envValues[$key] }
    return $default
}

function Wait-Healthy([string]$url, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) { return $true }
        } catch {
            # Not up yet (connection refused / 503 while starting). Keep polling.
        }
        Start-Sleep -Seconds 5
    }
    return $false
}

function Stop-App {
    $svc = Get-Service -Name $ServiceName
    if ($svc.Status -ne 'Stopped') {
        Write-Host "Stopping $ServiceName ..."
        Stop-Service -Name $ServiceName -Force
        $svc.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(90))
    }
}

function Start-App {
    Write-Host "Starting $ServiceName ..."
    Start-Service -Name $ServiceName
}

function Backup-Database($envValues) {
    if ($envValues.ContainsKey('DB_URL') -and $envValues['DB_URL'] -ne '') {
        Write-Warning 'DB_URL is set (external database such as Neon): skipping pg_dump. Back it up with your provider.'
        return
    }
    $pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
    if (-not $pgDump) {
        # Fall back to the newest PostgreSQL install under Program Files.
        $candidate = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\pg_dump.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($candidate) { $pgDump = $candidate.FullName } else {
            throw 'pg_dump not found. Install PostgreSQL client tools, add them to PATH, or pass -SkipDbBackup.'
        }
    } else {
        $pgDump = $pgDump.Source
    }

    $backupDir = Join-Path $AppDir 'backups'
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
    $stamp  = Get-Date -Format 'yyyyMMdd-HHmmss'
    $dbName = Get-Setting $envValues 'POSTGRES_DB' 'tms'
    $target = Join-Path $backupDir "$dbName-$stamp.dump"

    Write-Host "Backing up database $dbName to $target ..."
    $env:PGPASSWORD = Get-Setting $envValues 'POSTGRES_PASSWORD' ''
    try {
        & $pgDump `
            -h (Get-Setting $envValues 'DB_HOST' 'localhost') `
            -p (Get-Setting $envValues 'DB_PORT' '5432') `
            -U (Get-Setting $envValues 'POSTGRES_USER' 'postgres') `
            -F c -f $target $dbName
        if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE" }
    } finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }

    # Keep only the newest $KeepBackups dumps.
    Get-ChildItem $backupDir -Filter '*.dump' | Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $KeepBackups | Remove-Item -Force
}

# ---------- deploy ----------

if (-not (Test-Path $JarPath)) { throw "Jar not found: $JarPath" }
if (-not (Test-Path $envFile)) { throw "Missing $envFile - create it from .env.example before the first deploy." }
if (-not (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue)) {
    throw "Service $ServiceName is not installed. Run '$AppDir\tms-api.exe install' once (see deploy/windows/README.md)."
}

$envValues = Read-EnvFile $envFile
$port      = Get-Setting $envValues 'SERVER_PORT' '8080'
$healthUrl = "http://localhost:$port/management/health/readiness"

if (-not $SkipDbBackup) { Backup-Database $envValues }

Stop-App

if (Test-Path $liveJar) { Copy-Item $liveJar $backupJar -Force }
Copy-Item $JarPath $liveJar -Force

Start-App
Write-Host "Waiting up to $TimeoutSec s for $healthUrl ..."
if (Wait-Healthy $healthUrl $TimeoutSec) {
    Write-Host 'Deploy succeeded: application is healthy.'
    exit 0
}

Write-Warning "Application did not become healthy within $TimeoutSec s. Rolling back."
$errLog = Join-Path $AppDir 'tms-api.err.log'
if (Test-Path $errLog) {
    Write-Host '----- last 60 lines of tms-api.err.log -----'
    Get-Content $errLog -Tail 60
}
$outLog = Join-Path $AppDir 'tms-api.out.log'
if (Test-Path $outLog) {
    Write-Host '----- last 60 lines of tms-api.out.log -----'
    Get-Content $outLog -Tail 60
}

Stop-App
if (Test-Path $backupJar) {
    Copy-Item $backupJar $liveJar -Force
    Start-App
    if (Wait-Healthy $healthUrl $TimeoutSec) {
        Write-Warning 'Rolled back to the previous jar; it is healthy. Note: database migrations from the failed version are NOT undone.'
    } else {
        Write-Warning 'Rolled back, but the previous jar is not healthy either. Check the server manually.'
    }
} else {
    Write-Warning 'No previous jar to roll back to (first deploy). Service left stopped.'
}
exit 1
