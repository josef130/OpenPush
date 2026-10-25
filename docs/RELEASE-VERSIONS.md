# One release tag for SDK and Gradle plugin

JitPack runs the root multi-module project. openpushPublicationVersion=$VERSION
sets the same version for core, sdk, gradle-plugin, the plugin marker and the root
OpenPush alias. The plugin inherits the root version in this build. Do not combine
SDK 0.3.0 with plugin 0.2.0 as a fallback.

The public 0.3.0 build failed at SDK publication because the production HTTPS
endpoint was absent, before it reached gradle-plugin. Both 0.3.0 POMs returned
404 during verification. Updating GitHub files alone cannot fix an old tag's
failed immutable source snapshot. Create and verify a new tag after uploading
the corrected files; do not silently replace an existing tag.

## Local-only development publication (current mode)

jitpack.yml explicitly passes openpushDevelopmentPublication=true and
openpushDebugServerUrl=http://127.0.0.1:4310 to BOTH preflight and publication.
The root preflight runs before any module can publish to Maven local. No fake
production hostname is used. DEFAULT_SERVER_URL remains empty; only debuggable
consumer applications use DEBUG_SERVER_URL. These are development artifacts,
not production-ready SDK releases for apps distributed to real users.

USB testing still needs adb reverse tcp:4310 tcp:4310 and a Debug-only Android
network security rule allowing loopback HTTP. Outside USB, 127.0.0.1 on a phone
means that phone, not the dashboard computer. A local dashboard can send through
FCM, but automatic registration and device receipts need a reachable server.

## Consumer: change one property

In wasafat/gradle.properties:

```properties
openpushVersion=0.3.0
```

This is currently a BLOCKED requested version because its public build failed.
After a new tag succeeds, change only this property, e.g. to 0.4.0 if that is the
actual tag you created and verified. No new tag was created by these changes.

Project build.gradle:

```groovy
classpath "com.github.josef130.OpenPush:gradle-plugin:${providers.gradleProperty('openpushVersion').get()}"
```

app/build.gradle:

```groovy
implementation "com.github.josef130.OpenPush:sdk:${providers.gradleProperty('openpushVersion').get()}"
```

Both use the same property; JitPack must be in both existing repository blocks.
No verification-maven repository, mavenLocal or fallback to 0.2.0 is used.
The host keeps its existing Firebase exclusion, other dependencies and OneSignal.
The updated API is OpenPush.init(this, "APP_ID"); app/openpush.xml is absent.

## Production later

Remove the two development publication properties from jitpack.yml; supply the
real HTTPS origin via OPENPUSH_SERVER_URL or openpushDefaultServerUrl. Confirm
connectivity, registration, display/click receipts and routing before advertising
a production release. Never compile localhost as DEFAULT_SERVER_URL. Already
installed apps need an update when a compiled endpoint changes.
