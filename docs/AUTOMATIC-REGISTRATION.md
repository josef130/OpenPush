# Automatic Android registration (0.4.3 prerelease update)

New central-Firebase applications require no manual Android app binding and no openpush.xml.
Configure the application's FCM Service Account in the local dashboard, include the host's google-services.json and Google Services Gradle plugin, then initialize:

    io.openpush.sdk.OpenPush.init(this, "YOUR_OPENPUSH_APP_ID");

The SDK reads the host Firebase project and Android package. The server publishes an explicit project-challenge-v1 registration profile for applications without an existing package binding. A device remains pending until it receives and returns the existing FCM registration nonce. APP_ID, anonymous authentication and a self-reported package alone do not grant membership.

Existing package-bound applications retain their package checks and published clients remain supported. Legacy HTTP bootstrap/resource configuration APIs are unchanged. This update requires the matching dashboard and Firestore rules update before new unbound applications can register. It does not add collections, listeners, receipt writes, or per-notification registration reads. Package names are metadata in project-based mode, not an app-attestation claim; distinct apps requiring strong isolation should use distinct Firebase projects.

The 0.4.3 prerelease tag is being revised at the owner's request. Existing installed applications do not update automatically. Maven/JitPack caches may retain the previous artifact; verify the resolved artifact/commit and use a new immutable tag if the provider will not rebuild the revised tag.
