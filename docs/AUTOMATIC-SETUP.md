# Android SDK setup — App ID API (next release)

The published SDK and Gradle plugin 0.2.0 are the existing baseline. Source edits do
NOT update that tag. init(Activity, APP_ID), automatic bootstrap and the bundled
icon below are prepared for a NEW release; local verification is 0.2.1-local.
Do not tell consumers to call the new overload on an unverified existing 0.2.0.

## Prerequisites, once per application

Configure the exact applicationId in Firebase. Keep app/google-services.json and
the Google Services Gradle plugin. Verify the matching Firebase Admin credentials
in OpenPush and save the Android package binding in the dashboard. Service Account
keys stay on the server. No client XML or client key is required for the new API.

## 1. Add the Android library

Add JitPack to the existing dependency repositories. After the new release is
published and verified, replace NEXT_RELEASE with that tag:

```groovy
implementation 'com.github.josef130.OpenPush:sdk:NEXT_RELEASE'
```

Keep existing compatible host Firebase versions/BoM. Inspect the resolved graph;
wasafat's host-specific Messaging exclusion is not a universal install step.

## 2. Gradle plugin

```groovy
// Project build.gradle
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.josef130.OpenPush:gradle-plugin:NEXT_RELEASE'
    }
}
// app/build.gradle (retain existing plugins)
plugins { id 'io.openpush.android' }
```

The new plugin does not require openpush.xml. It remains useful for optional
legacy configuration; the new runtime API does not depend on the importer.
No includeBuild, local source directory or SDK verification repository is needed
for a verified public release.

## 3. Initialize Home / MainActivity

```java
io.openpush.sdk.OpenPush.init(this, "APP_ID_FROM_OPENPUSH");
```

Call after super.onCreate. No custom Application or manually created FCM service
is needed when OpenPush owns the receiver. App ID is validated and persisted;
Firebase projectId is read from FirebaseApp and checked against optional legacy
configuration. A missing Firebase app/project or malformed ID produces a clear
exception. Repeated initialization of the same app reuses the instance/listeners.

Background registration retrieves a PUBLIC per-app enrollment key from
/api/sdk/bootstrap using the App ID, actual Firebase projectId and package name.
The server rejects a mismatched/unconfigured app; the response exposes no Admin
credentials, device tokens, installation secrets or sending capability. The SDK
checks response scope, caches the public key and retains installation-secret/token
rotation and retry behavior. This is public enrollment, not app attestation.

The bundled drawable ic_stat_onesignal_default.png is the default small icon used
by Notification.Builder. Optional drawable/openpush_notification_icon retains its
legacy precedence; otherwise io.openpush.notification_icon metadata can customize
it. The SDK supplies its own existing default asset, so no icon file is required.

Default approved hosts: www.google.com, google.com, play.google.com. Existing
optional openpush_web_hosts and deep-link rules remain supported. Google Play,
web resolution, unique PendingIntent and at-most-once click behavior are unchanged.

## Backend default: required before publication

No real public HTTPS endpoint has been supplied for this installation. We do NOT
invent a production hostname or ship localhost as a production default. The SDK
publisher must set openpushDefaultServerUrl or OPENPUSH_SERVER_URL to the actual
HTTPS origin. Public Maven-local/JitPack publication rejects a missing/non-HTTPS
origin. Once compiled into the release, consumers need not pass it in Activities.
Optional existing resource configuration can override it for a self-hosted backend.

USB-only verification builds set -PopenpushDebugServerUrl=http://127.0.0.1:4310,
use adb reverse tcp:4310 tcp:4310 and a debuggable consumer. DEFAULT_SERVER_URL
stays empty until the real HTTPS origin is supplied. Release consumers never use
DEBUG_SERVER_URL; public publication also rejects any configured debug endpoint.
This verification artifact is not suitable for Google Play production.

The endpoint is central in the SDK build, not in Activity code. Changing a compiled
endpoint requires a new SDK and consumer app update. Keeping a stable HTTPS domain
allows its infrastructure to move through DNS without changing installed apps.

## Compatibility and device checks

Old init(Activity), explicit builders and optional XML remain supported. Keep any
existing FirebaseMessagingService and use explicit delegation where required;
FCM does not independently deliver the same message to multiple services. Do not
silently remove another push SDK. Android 13 permission and OEM battery policies
remain user-controlled. Force-stop delivery is not guaranteed.

Check real display receipt before click, click receipt after one tap, image/text,
foreground/background and process removal. Robolectric and successful builds do
not prove arrival or Google Play navigation on a physical device.
