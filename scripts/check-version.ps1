$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Get-Content -LiteralPath (Join-Path $projectRoot 'app/build.gradle') -Raw
$version = [regex]::Match($gradle, "versionName '([^']+)'").Groups[1].Value
$code = [regex]::Match($gradle, 'versionCode (\d+)').Groups[1].Value
if (-not $version -or -not $code) { throw 'Missing Android version metadata.' }
$readme = Get-Content -LiteralPath (Join-Path $projectRoot 'README.md') -Raw
$history = Get-Content -LiteralPath (Join-Path $projectRoot 'CHANGELOG.md') -Raw
if (-not $readme.StartsWith("# NusukConnec $version")) { throw 'README version differs from app/build.gradle.' }
if (-not $history.Contains("## $version (versionCode $code)")) { throw 'Changelog entry is missing.' }
$archives = Join-Path $projectRoot 'archives'
$manifest = Get-Content -LiteralPath (Join-Path $archives 'manifest.json') -Raw | ConvertFrom-Json
foreach ($entry in $manifest) {
    if ([IO.Path]::GetFileName($entry.file) -ne $entry.file) { throw 'Archive filename must be a simple filename.' }
    $actual = (Get-FileHash -LiteralPath (Join-Path $archives $entry.file) -Algorithm SHA256).Hash
    if ($actual -ine $entry.sha256) { throw "Archive checksum mismatch: $($entry.file)" }
}
Write-Output "Version $version / code ${code}: documentation and $($manifest.Count) archived APK checksums agree."
