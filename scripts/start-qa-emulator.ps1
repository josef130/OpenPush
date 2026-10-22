param(
    [Parameter(Mandatory = $true)][string]$SdkPath,
    [Parameter(Mandatory = $true)][string]$ImagePath,
    [switch]$SoftwareOnly
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath (Join-Path $ImagePath 'system.img'))) { throw 'System image missing' }
$repo = Split-Path $PSScriptRoot -Parent
$avdRoot = Join-Path $repo 'build\qa-avd'
$avdPath = Join-Path $avdRoot 'OpenPush_QA.avd'
New-Item -ItemType Directory -Force -Path $avdPath | Out-Null
$image = (Resolve-Path -LiteralPath $ImagePath).Path.Replace('\', '/') + '/'
$data = $avdPath.Replace('\', '/')
$source = Get-Content -LiteralPath (Join-Path $ImagePath 'source.properties')
$apiLine = $source | Where-Object { $_ -like 'AndroidVersion.ApiLevel=*' } | Select-Object -First 1
$api = $apiLine.Split('=', 2)[1]
@"
avd.ini.encoding=UTF-8
path=$data
target=android-$api
"@ | Set-Content -LiteralPath (Join-Path $avdRoot 'OpenPush_QA.ini') -Encoding ASCII
@"
avd.ini.encoding=UTF-8
AvdId=OpenPush_QA
avd.ini.displayname=OpenPush QA isolated emulator
image.sysdir.1=$image
target=android-$api
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.sdCard=no
disk.dataPartition.size=2G
showDeviceFrame=no
PlayStore.enabled=true
tag.id=google_apis_playstore
tag.display=Google Play
"@ | Set-Content -LiteralPath (Join-Path $avdPath 'config.ini') -Encoding ASCII
$env:ANDROID_AVD_HOME = $avdRoot
$env:ANDROID_HOME = $SdkPath
$stdout = Join-Path $avdRoot 'emulator.stdout.log'
$stderr = Join-Path $avdRoot 'emulator.stderr.log'
$emulatorArgs = @(
    '-avd', 'OpenPush_QA', '-port', '5556', '-no-window', '-no-audio', '-no-snapshot',
    '-no-boot-anim', '-show-kernel', '-gpu', 'swiftshader', '-feature', '-Vulkan', '-memory', '2048', '-cores'
)
if ($SoftwareOnly) { $emulatorArgs += @('1', '-accel', 'off') } else { $emulatorArgs += '2' }
$process = Start-Process -FilePath (Join-Path $SdkPath 'emulator\emulator.exe') -ArgumentList $emulatorArgs -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
[pscustomobject]@{ launcherPid = $process.Id; serial = 'emulator-5556'; avd = 'OpenPush_QA'; imageApi = $api } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $avdRoot 'process.json') -Encoding UTF8
Write-Output ('Started isolated QA emulator; launcher PID={0}; serial=emulator-5556; API={1}' -f $process.Id, $api)
