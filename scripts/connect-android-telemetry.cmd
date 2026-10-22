@echo off
setlocal
rem Debug USB transport only. Production SDKs need a reachable HTTPS endpoint.
set "OPENPUSH_ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%OPENPUSH_ADB%" (
  echo Android SDK adb.exe not found. Install Android SDK platform-tools.
  exit /b 1
)
"%OPENPUSH_ADB%" devices -l
"%OPENPUSH_ADB%" reverse tcp:4310 tcp:4310
if errorlevel 1 exit /b 1
echo OpenPush debug telemetry connected to http://127.0.0.1:4310/api/events
echo Keep the local server running. Re-run this command after reconnecting USB.
echo Background retries may take time after a previous connection failure.
