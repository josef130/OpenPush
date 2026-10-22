# Automatic Android setup — unreleased

## 1. Gradle and Firebase

Add JitPack to dependencyResolutionManagement repositories. Use the approved new
release when published; the existing 0.1.0 has not been overwritten. Local verification:

```groovy
// Local verification ONLY: repository build/verification-maven
implementation 'com.github.josef130:OpenPush:0.1.0-local'
```

For this checkout's already-built local artifact, add this repository inside
settings.gradle's dependencyResolutionManagement.repositories:

```groovy
maven { url uri('C:/xampp/htdocs/OpenPush/OpenPush/build/verification-maven') }
```

In app/build.gradle, apply `id 'com.google.gms.google-services'` alongside the
existing Android application plugin. Its version belongs in the host's root plugin
configuration; preserve an existing compatible version. This checkout uses 4.5.0.

The root POM pulls sdk, core and Firebase Messaging 25.0.1. Enable
`android.useAndroidX=true` in gradle.properties. Keep a compatible existing Firebase
BoM and verify resolution. Configure the Google Services plugin and put your app's
Firebase client google-services.json in app/. API 24+ and Google Play services required.

## 2. App configuration

In the dashboard configure Firebase Admin credentials, then Install and Test →
Automatic Android SDK configuration. Generate configuration for the exact
applicationId and save openpush.xml in app/src/main/res/values/.
It contains openpush_server_url, openpush_app_id, openpush_project_id,
openpush_client_key and comma-separated openpush_web_hosts. No Admin secrets.
Add a monochrome drawable/openpush_notification_icon with transparent background.

The current server remains local-only. USB QA uses adb reverse tcp:4310 tcp:4310
and debug loopback HTTP. Deployed clients need a reachable HTTPS enrollment service;
deploying a public service also needs authentication/attestation and abuse hardening.
Client enrollment keys identify apps and permit registration only, not sending or
dashboard access. Installation secrets are generated privately on each device.

## 3. Activity

```java
import io.openpush.sdk.OpenPush;
// MainActivity.onCreate, after super.onCreate:
OpenPush.init(this);
```

Manifest Merger adds initialization, the fallback FCM service and retry jobs. The
SDK restores configuration in a cold process and updates registration on token
rotation and foreground entry, preserving an existing administrator unsubscribe.
Android 13+ permission is requested once; a refusal is respected. No custom
Application or click forwarding is needed for dashboard data notifications.

Existing FirebaseMessagingService hosts must keep their service, remove
io.openpush.sdk.OpenPushMessagingService with tools:node="remove", and call
OpenPush.receive(this,message) in onMessageReceived (return if true), plus
OpenPush.refreshRegistration(this) in onNewToken, retaining existing callbacks.
The library fallback has priority -100, below ordinary host services and above
Firebase's default -500 service. Multiple host services require explicit resolution;
FCM does not broadcast a message independently to all of them.

Advanced routing still supports the existing builder API. Automatic deep-link
configuration may use a string-array openpush_deep_links whose entries are
scheme|host|path|fully.qualified.Activity; matching manifest filters remain required.
Only approved website hosts open. Google Play routing keeps the existing official
Store selection and HTTPS fallback. Click ledger and unique PendingIntent IDs remain.

Test dashboard → phone → displayed receipt before tap → clicked receipt after tap.
Plain Firebase Console Notification + Data has different background system behavior;
existing hosts may still need onCreate/onNewIntent forwarding for those messages.
Force stop and OEM restrictions cannot be bypassed or treated as guaranteed delivery.
