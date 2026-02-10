param(
    [Parameter(Mandatory = $true)]
    [string]$BackupZip,
    [string]$DataDir = "data",
    [switch]$Force,
    [switch]$Purge,
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

$args = @("restore", $BackupZip, $DataDir)
if ($Force) { $args += "--force" }
if ($Purge) { $args += "--purge" }
if ($IncludeCorrupt) { $args += "--include-corrupt" }

& java -cp $Jar com.example.chess.server.tools.DataBackupTool @args
if ($LASTEXITCODE -ne 0) {
    throw "Restore failed."
}
