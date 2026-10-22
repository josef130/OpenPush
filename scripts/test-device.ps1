param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$qaRoot = Split-Path -Parent $PSScriptRoot
$qaApk = Join-Path $qaRoot 'sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk'
if (!(Test-Path -LiteralPath $Adb)) { throw 'Set ANDROID_HOME or supply -Adb.' }
if (!(Test-Path -LiteralPath $qaApk)) { throw 'Build :sample:assembleDebugAndroidTest first.' }
# Target must be the disposable OpenPush sample, configured for Firebase and permission granted.
# SDK contracts reset the sample click ledger; never point this runner at a production app.
& $Adb -s $Serial install -r $qaApk
if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
$qaClasses = 'io.openpush.sdk.ClickTest,io.openpush.sample.DeviceAcceptanceTest#localHostAndOutstandingNotifications,io.openpush.sample.DeviceAcceptanceTest#localRejectedIntentsAndReplay,io.openpush.sample.DeviceAcceptanceTest#localDestinationClassificationContracts'
$qaOutput = & $Adb -s $Serial shell am instrument -w -e class $qaClasses io.openpush.sample.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$qaInstrumentationExit = $LASTEXITCODE
$qaResult = $qaOutput -join "`n"
$qaEvidence = Join-Path $qaRoot 'verification/device-contracts.txt'
$qaResult | Set-Content -Encoding utf8 -LiteralPath $qaEvidence
Write-Output $qaResult
# Android's am instrument can return process exit 0 even when JUnit assertions fail.
& $Adb -s $Serial shell am start -W -n io.openpush.sample/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Could not reopen sample after instrumentation; open it manually before testing FCM.' }
if ($qaInstrumentationExit -ne 0 -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' -or
        $qaResult -notmatch 'OK \(8 tests\)') { throw 'Physical-device contracts did not pass; inspect saved output.' }
