# OpenPush Gradle integration (unreleased)

The plugin id is io.openpush.android. This is a local included build, not a
published Gradle Plugin Portal/JitPack artifact. No published 0.1.0 was changed.

## One-time prerequisites

Configure Firebase for the real application package and configure the matching
Firebase Admin credentials on the OpenPush server. Keep google-services.json in
app/ and apply the Google Services plugin as required by Firebase. Keep all Admin
credentials outside Android. Put the OpenPush client resource XML at app/openpush.xml.
It contains server/app/project/public enrollment settings; the Activity needs no ID.
A reachable HTTPS server is required for distributed applications.

## Three code edits (current local verification)

1. Add the dependency in app/build.gradle:

```groovy
implementation 'com.github.josef130:OpenPush:0.1.0-local'
```

2. Apply the Gradle plugin in app/build.gradle:

```groovy
plugins { id 'io.openpush.android' }
```

For this unpublished working copy, settings.gradle resolves that plugin with:

```groovy
pluginManagement { includeBuild('../OpenPush/gradle-plugin') }
```

The dependency repository is the local OpenPush/build/verification-maven directory.
These two local resolution paths will be replaced by public repositories only
when the SDK and plugin are approved and actually published.

3. In the main Activity's onCreate after super.onCreate:

```java
io.openpush.sdk.OpenPush.init(this);
```

No custom Application or manually created Firebase service is required when
OpenPush is the sole FCM owner. Existing host FCM services need documented delegation.
Do not initialize another push SDK with a competing receiver.

## Behavior

The plugin reads app/openpush.xml, validates required resource names and generates
Android string resources. It does not contact Firebase or upload any file. It
rejects XML DTDs. Runtime SDK validation still checks project, URL and client key.
Release packaging rejects a non-HTTPS server endpoint. Permissions and manufacturer
battery settings remain controlled by Android and the user.
