# OpenPush Gradle plugin (next release)

Current published baseline: com.github.josef130.OpenPush:gradle-plugin:0.2.0.
The source change makes openpush.xml optional for the new init(Activity, APP_ID)
SDK API. Existing published files are not overwritten. Local verification uses
0.2.1-local; publish and verify a NEW tag before documenting it as available.

See ../docs/AUTOMATIC-SETUP.md for the complete three-step public integration.
The plugin JAR is resolved from JitPack via root buildscript classpath and applied
with id io.openpush.android in app/build.gradle. Source includeBuild is unnecessary.

If app/openpush.xml exists, the importer still parses/validates it securely and
generates its resources for legacy integrations. If absent, it does nothing and
does not block Android DSL evaluation. The new SDK supplies its icon itself.
No network request or Service Account processing occurs in this build plugin.

The publisher must provide a real HTTPS OPENPUSH_SERVER_URL before PRODUCTION
publication. The current jitpack.yml explicitly opts into DEVELOPMENT-only USB
publication; its production endpoint remains empty. See ../docs/RELEASE-VERSIONS.md.
