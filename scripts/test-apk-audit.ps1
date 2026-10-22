$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$repo = Split-Path $PSScriptRoot -Parent
$fixtures = Join-Path $repo 'build\audit-tool-tests'
New-Item -ItemType Directory -Force -Path $fixtures | Out-Null
function New-AuditFixture([string]$Path, [string]$Content) {
    $file = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create)
    $zip = [System.IO.Compression.ZipArchive]::new($file, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry = $zip.CreateEntry('assets/fixture.txt')
        $writer = [System.IO.StreamWriter]::new($entry.Open())
        try { $writer.Write($Content) } finally { $writer.Dispose() }
    } finally { $zip.Dispose(); $file.Dispose() }
}
$positive = Join-Path $fixtures 'synthetic-positive.zip'
$negative = Join-Path $fixtures 'synthetic-client-identifiers.zip'
New-AuditFixture $positive 'Synthetic fixture only: -----BEGIN PRIVATE KEY----- NOT A REAL KEY'
New-AuditFixture $negative '{"google_api_key":"AIza-not-a-server-secret","google_app_id":"client-identifier"}'
$positiveReport = Join-Path $fixtures 'positive.json'
$negativeReport = Join-Path $fixtures 'negative.json'
$rejected = $false
try { & (Join-Path $PSScriptRoot 'audit-apk.ps1') -ApkPath $positive -OutputPath $positiveReport -ArtifactLabel 'synthetic tool test, not an app' | Out-Null }
catch { $rejected = $true }
if (-not $rejected) { throw 'Audit failed to flag the positive fixture' }
$record = Get-Content -Raw -LiteralPath $positiveReport | ConvertFrom-Json
if ($record.status -ne 'REVIEW_REQUIRED' -or @($record.findings | Where-Object { $_.rule -eq 'private_key_pem' }).Count -ne 1) {
    throw 'Positive audit did not produce the expected evidence'
}
& (Join-Path $PSScriptRoot 'audit-apk.ps1') -ApkPath $negative -OutputPath $negativeReport -ArtifactLabel 'synthetic client identifier fixture, not an app' | Out-Null
$record = Get-Content -Raw -LiteralPath $negativeReport | ConvertFrom-Json
if ($record.status -ne 'NO_MATCHING_SECRET_INDICATORS') { throw 'Client identifiers were incorrectly classified as server credentials' }
Write-Output 'PASS: APK audit detects the private-key indicator and permits ordinary Firebase client identifiers'
