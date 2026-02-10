param(
    [string]$DataDir = "data",
    [string]$BackupDir = "backups",
    [int]$Retain = 7,
    [switch]$IncludeCorrupt,
    [string]$Jar = "server/target/chess-server.jar",
    [switch]$Build
)

if ($Build) {
    & .\mvnw -q -pl server -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed."
    }
}

if (!(Test-Path $Jar)) {
    throw "Missing jar: $Jar"
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$ts = Get-Date -Format "yyyyMMddHHmmss"
$zip = Join-Path $BackupDir ("chess-data-$ts.zip")

$args = @("backup", $DataDir, $zip)
if ($IncludeCorrupt) { $args += "--include-corrupt" }

& java -cp $Jar com.example.chess.server.tools.DataBackupTool @args
if ($LASTEXITCODE -ne 0) {
    throw "Backup failed."
}

if ($Retain -gt 0) {
    Get-ChildItem -Path $BackupDir -Filter "chess-data-*.zip" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $Retain |
        Remove-Item -Force
}

Write-Host "Backup complete: $zip"
