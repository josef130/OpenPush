param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { $JavaHome = 'C:\Program Files\Android\Android Studio\jbr' }
$repo = Split-Path $PSScriptRoot -Parent
$output = Join-Path $repo 'core\build\contract-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(Get-ChildItem (Join-Path $repo 'core\src') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
& (Join-Path $JavaHome 'bin\javac.exe') --release 8 -encoding UTF-8 -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& (Join-Path $JavaHome 'bin\java.exe') -cp $output io.openpush.core.RouteTest
if ($LASTEXITCODE -ne 0) { throw 'Contract tests failed' }
