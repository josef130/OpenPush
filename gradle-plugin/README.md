# OpenPush Gradle plugin — Maven / JitPack integration

Publication is prepared but NOT uploaded. Existing public 0.1.0 does not gain this
plugin automatically. NEW_RELEASE_TAG below is a placeholder for a NEW GitHub tag
whose JitPack build has actually succeeded. Do not paste 0.1.0 or a guessed version.

## 1. Project-level build.gradle: plugin classpath

Merge these entries into the existing buildscript block; retain AGP and other plugins:

```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.josef130.OpenPush:gradle-plugin:NEW_RELEASE_TAG'
    }
}
```

This resolves the build-time plugin JAR. It does not go into the Android APK.
The plugin ID is not a Maven coordinate: io.openpush.android remains unchanged.
No includeBuild, local source directory or verification-maven path is needed after
publication. Plugins DSL with the version omitted works when its implementation
is already on the root buildscript classpath. The older apply-plugin syntax is
also supported.

## 2. app/build.gradle: plugin and Android library

```groovy
plugins {
    id 'com.android.application'
    id 'com.google.gms.google-services'
    id 'io.openpush.android'
}

dependencies {
    implementation 'com.github.josef130.OpenPush:sdk:NEW_RELEASE_TAG'
}
```

Use the sdk module explicitly to avoid depending on a repository-wide aggregate
that might include build-tool modules. sdk's POM supplies core and Firebase Messaging.
Keep any existing host Firebase declaration/BoM; inspect Gradle's resolved version.
wasafat specifically retains its older validated Messaging 21.1.0 by excluding the
transitive Messaging dependency. This exclusion is not a universal install step.

Also allow JitPack in the application's normal dependency repositories, usually
settings.gradle (merge into the existing block):

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

buildscript.repositories and dependencyResolutionManagement.repositories have
separate purposes: one resolves plugins, the other the Android dependency.

## 3. Main Activity

In onCreate after super.onCreate:

```java
io.openpush.sdk.OpenPush.init(this);
```

No custom Application or manually created Firebase service is required when
OpenPush is the sole FCM owner. Existing host services need explicit delegation;
do not initialize another competing push SDK.

## Mandatory app configuration

Configure Firebase for the real application package. Keep google-services.json
in app/ and configure matching Firebase Admin credentials on the dashboard server
ONLY. Put the OpenPush client resource XML at app/openpush.xml (server URL, app ID,
project ID, public enrollment key and allowed hosts). Supply the notification icon.
The Activity needs no App ID. A distributed app requires a reachable HTTPS backend.
Gradle plugin publication cannot eliminate these app-specific configuration inputs.

The plugin rejects DTDs, generates Android resources and blocks release packaging
with non-HTTPS endpoints. It never uploads credentials or contacts Firebase.
Phone notification permissions and battery policies remain user controlled.

## Maintainer publication

The plugin is a normal :gradle-plugin subproject with maven-publish. Its publication
is com.github.josef130.OpenPush:gradle-plugin:TAG. jitpack.yml explicitly publishes
core, sdk, gradle-plugin and the existing root POM to Maven local during JitPack's
build, using openpushPublicationVersion=$VERSION. It does not build sample or
require the sample's real google-services.json.

Publish a new approved tag; do not replace 0.1.0. Verify the remote JAR and POM,
then sync/build an independent Android consumer using only HTTPS repositories.
Local Maven tests are necessary preparation, not evidence of successful JitPack
publication. Tested local toolchain: JDK17 / Gradle8.14.3 / Android host AGP8.11.1.
