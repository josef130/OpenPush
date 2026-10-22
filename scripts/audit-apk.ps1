param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$OutputPath = '',
    [string]$ArtifactLabel = 'local-only sample APK'
)
$ErrorActionPreference = 'Stop'
if (-not $OutputPath) { $OutputPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'verification\apk-audit.local.json' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$rules = [ordered]@{
    'private_key_pem' = '-----BEGIN (?:RSA |EC |ENCRYPTED |OPENSSH )?PRIVATE KEY-----'
    'service_account_json' = '"type"\s*:\s*"service_account"'
    'private_key_json_value' = '"private_key"\s*:\s*"[^"\r\n]{16,}'
    'legacy_fcm_server_key' = 'AAAA[A-Za-z0-9_-]{6,32}:APA91[A-Za-z0-9_-]{25,}'
    'oauth_access_token' = 'ya29\.[A-Za-z0-9_-]{40,}'
}
$findings = [System.Collections.Generic.List[object]]::new()
$entries = 0
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName.EndsWith('/')) { continue }
        $entries++
        $stream = $entry.Open()
        $memory = [System.IO.MemoryStream]::new()
        try { $stream.CopyTo($memory); $bytes = $memory.ToArray() }
        finally { $stream.Dispose(); $memory.Dispose() }
        $views = @([System.Text.Encoding]::UTF8.GetString($bytes), [System.Text.Encoding]::Unicode.GetString($bytes))
        if ($bytes.Length -gt 1) { $views += [System.Text.Encoding]::Unicode.GetString($bytes, 1, $bytes.Length - 1) }
        foreach ($rule in $rules.Keys) {
            $found = $false
            foreach ($view in $views) { if ([regex]::IsMatch($view, $rules[$rule])) { $found = $true; break } }
            if ($found) { $findings.Add([pscustomobject]@{ entry = $entry.FullName; rule = $rule }) }
        }
        if ($entry.FullName -match '(?i)(service[-_]?account[^/]*\.json|\.(p12|pfx|key|pem))$') {
            $findings.Add([pscustomobject]@{ entry = $entry.FullName; rule = 'credential_file_candidate' })
        }
    }
} finally { $archive.Dispose() }
$report = [ordered]@{
    artifact = $resolvedApk
    artifactLabel = $ArtifactLabel
    sha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()
    entriesScanned = $entries
    status = $(if ($findings.Count -eq 0) { 'NO_MATCHING_SECRET_INDICATORS' } else { 'REVIEW_REQUIRED' })
    rules = @($rules.Keys) + 'credential_file_candidate'
    findings = @($findings.ToArray())
    limitations = 'Static indicator scan only. Does not prove absence of arbitrary, encrypted, encoded, downloaded or runtime secrets. Client Firebase API identifiers are not server private keys. Repeat on the real configured APK.'
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output ('APK audit: {0}; entries={1}; findings={2}; sha256={3}' -f $report.status, $entries, $findings.Count, $report.sha256)
if ($findings.Count -ne 0) { throw 'Potential credential indicators found; see entry/rule metadata in audit report. No secret values were printed.' }
