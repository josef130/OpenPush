# Local Firebase setup

The sample is part of this SDK checkout so Gradle and device contracts stay independent of the dashboard. The real `sample/google-services.json` remains local and is ignored. Obtain your own client file from Firebase for `io.openpush.sample`; do not commit it. An Admin SDK Service Account file is never used by Android.

Build without Firebase configuration for local contracts:

    gradlew.bat :sdk:assembleDebug :sdk:testDebugUnitTest :core:check :sample:assembleDebug -PopenpushLocalOnly

Build a configured sample after placing your own client JSON:

    gradlew.bat :sample:assembleDebug

For local dashboard telemetry only, pass `-PopenpushTelemetryEndpoint=http://127.0.0.1:4310/api/events` and run `scripts\connect-android-telemetry.cmd` after USB reconnection. Production requires your own reachable HTTPS endpoint. Dashboard code is not required to build this SDK.

The original manual sample now builds as `:sample:assembleManualDebug` and launches
MainActivity. Automatic integration builds as `:sample:assembleAutomaticDebug` and
launches SimpleActivity without a custom Application or host messaging service.
Use ignored automatic-test.properties with serverUrl, appId, projectId, clientKey
for local QA (or the corresponding openpushServerUrl/AppId/ProjectId/ClientKey
Gradle properties). Never add server credentials to this file.

Local Maven checks use build/verification-maven and 0.1.0-local. No Git repository
or remote release has been created/updated by this work. The raw AAR alone does
not embed core or Firebase; the configured Maven publications include their metadata.
See docs/AUTOMATIC-SETUP.md.
