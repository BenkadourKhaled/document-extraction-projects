[CmdletBinding()]
param(
    [int]$Port = 18080,
    [switch]$Ollama,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'

$apiDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $apiDir '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env and set the secrets."
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $matches[1]
        $value = $matches[2].Trim()
        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        Set-Item -Path "Env:$name" -Value $value
    }
}

foreach ($required in @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')) {
    if (-not (Get-Item -Path "Env:$required" -ErrorAction SilentlyContinue)) {
        throw "Missing $required in .env"
    }
}

if ($Rebuild) {
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvn) {
        throw "mvn.cmd not found on PATH. Build the jar from your IDE or add Maven to PATH."
    }
    Push-Location $apiDir
    try {
        & $mvn.Source -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
    } finally {
        Pop-Location
    }
}

$targetDir = Join-Path $apiDir 'target'
$jar = Get-ChildItem -LiteralPath $targetDir -Filter 'document-extraction-api-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|javadoc|original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "No API jar found in $targetDir. Build first with: mvn -B -DskipTests package"
}

$env:SPRING_PROFILES_ACTIVE = 'postgres'
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/$($env:POSTGRES_DB)"
$env:DATABASE_USERNAME = $env:POSTGRES_USER
$env:DATABASE_PASSWORD = $env:POSTGRES_PASSWORD
$env:STORAGE_TYPE = 'local'
$env:STORAGE_LOCAL_PATH = Join-Path $apiDir 'data\storage'
$env:SERVER_PORT = "$Port"
if ($Ollama) {
    $env:OLLAMA_ENABLED = 'true'
} else {
    $env:OLLAMA_ENABLED = 'false'
}

Write-Host "PostgreSQL : $($env:DATABASE_URL) (user $($env:DATABASE_USERNAME))"
Write-Host "API        : http://localhost:$Port"
Write-Host "Ollama     : $($env:OLLAMA_ENABLED)"
Write-Host "Jar        : $($jar.FullName)"

Push-Location $apiDir
try {
    & java -jar $jar.FullName
} finally {
    Pop-Location
}
