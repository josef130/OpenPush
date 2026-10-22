# Verification and release gates

## Reproducible commands

Use JDK 17, Gradle wrapper 8.14.3 and Android SDK Platform 35.
Set JAVA_HOME and ANDROID_HOME (or local.properties sdk.dir).
On Windows use gradlew.bat; elsewhere ./gradlew.

```text
gradlew.bat :core:contractTest :sdk:assembleDebug :sdk:assembleRelease :sdk:testDebugUnitTest :sdk:lintDebug
gradlew.bat :sdk:assembleDebugAndroidTest
gradlew.bat -PopenpushCompileOnly :sample:compileDebugJavaWithJavac
gradlew.bat :sdk:dependencies --configuration releaseRuntimeClasspath
gradlew.bat :sdk:connectedDebugAndroidTest
```

For standalone core checks use powershell -File scripts/test-core.ps1 -JavaHome '<JDK path>'.
The script compiles all core sources with javac --release 8 and executes RouteTest, including
the registry/ledger checks. It exits nonzero on assertion or compilation failure.

The sample's compile-only mode omits Google Services and never fabricates Firebase config.
It verifies Java, resources and manifest merging; it is NOT a runnable FCM setup. Normal
sample builds require sample/google-services.json from a real project. The SDK module
requires no JSON. Do not use task exclusion to skip Google Services: generated-resource
providers still depend on that task completing.

## Automated coverage

core/src/test: executable assertions for envelope validation, unsafe URLs, version/ID/type
rules, bounded arguments, immutable arguments, registered/unknown/rejected action resolution,
registry snapshotting, duplicate claims across adapters, eviction and persistence failure.

sdk/src/test: Robolectric Android behavior checks on API 28 plus a permission check on API 33:
screen and custom action routing, bounded persistent deduplication across adapters, malformed
extras, unknown actions/screens, exact HTTPS hostname approval, namespaced arguments, safe
handler/diagnostics failure, approved deep-link component resolution, undeclared destinations,
unsafe/traversal paths, local presentation without navigation, preserved host channel name,
independent PendingIntent payloads, disabled channel and notification permission denial/grant.

Robolectric simulates Android. The PackageManager deep-link test supplies resolution fixtures;
it is not proof of a device's manifest filter behavior or App Link domain verification.
Core failed-storage checks use a failing Store fixture. Preference/FS failures on actual
Android devices and full process death still require instrumentation/manual acceptance.

Five sdk/src/androidTest tests cover repeated calls/new adapter, independent IDs, rejection
without claiming, malformed extras, callback failure and 128-ID eviction. Packaging this APK
does NOT execute those tests. Run connectedDebugAndroidTest on a configured emulator/device.
A new adapter does not simulate killing/restarting an Android process. On the tested OEM,
the standalone APK was refused by package verification; sample sourceSets reuses these exact
tests under the sample's current target SDK without changing the library/host target.
Build `:sample:assembleDebugAndroidTest`, install the normal configured sample APK, enable
notifications, then run `powershell -File scripts/test-device.ps1 -Serial '<adb device>'`.
The script runs eight selected tests (including local destination classification) and checks JUnit output because adb may exit 0 on failure.
It resets the disposable sample click ledger. Do not run against production app data.
Do not run the entire DeviceAcceptanceTest class blindly: remote probes require named Console
notifications and qaTitle/qaExpected arguments. Token capture is private and opt-in.

## Host compatibility

The SDK releaseRuntimeClasspath should contain core ONLY, no Firebase BoM or Messaging.
SDK compileOnly uses Messaging 25.0.1; the sample/tests explicitly supply that version at runtime.
Inspect a real host using dependencyInsight for firebase-messaging and compare its resolved
Firebase graph before/after adding OpenPush. Do not upgrade/downgrade existing host dependencies
only to match the sample. Other versions and third-party notification SDK combinations are
not certified by the sample's tests.

The merged sample manifest must contain one host MessagingService with MESSAGING_EVENT.
Firebase's own lower-priority fallback service and component/transport services are normal.
Test that existing non-OpenPush receipt, token refresh, deleted messages and business behavior
still run. OpenPush never registers a competing host service or silently discovers another
service; integration is explicit and host-specific.

## Real-device acceptance

Actual API 34 results and per-scenario limitations are recorded in
[the practical report](PRACTICAL-TEST-REPORT.ar.md). The following is the broader test matrix,
not a claim that every listed case has passed.

Record device, API, payload ID, observed result and log only fixed diagnostic events.
Use API 24, 26, 31/32, 33 and 35 with Google Play services, including a physical OEM device.
Exercise notification-only, notification+data and future sender data-only separately.

- Console title/body + v1 fields: foreground local display, background FCM display; tap
  opens the intended destination once and passes article_id/level/coupon correctly.
- Background notification-only opens home without routing. Foreground notification-only
  remains host-owned and is displayed by the sample's ordinary-notification host handler.
- Test Home background, cold start, ordinary process kill, recents swipe, Settings force-stop
  followed by manual reopening, onNewIntent, rotation and actual process restart.
- Tap two notifications with different IDs; each keeps its own payload. Repeated callbacks
  do not duplicate a retained ID. Clearing data/reinstall can reset claims.
- HTTPS: approved host, unapproved host, malformed URL, no browser, and verified/unverified
  App Links. Deep links: declared/disabled/undeclared/ambiguous component, wrong scheme/host,
  path-boundary mismatch, encoded traversal, direct external Activity launch validation.
- Missing ID/target, unsupported version/action/screen, non-string extras, invalid parameters:
  no navigation or crash. Invalid background Console payloads may still display through FCM.
- Permission grant/deny/revoke, app notifications off and channel off: respect system settings.
- Confirm route authorization after login and host back-stack behavior, including splash entry.
- Confirm existing host service behavior and Firebase dependency resolution before/after SDK.
- Offline, Doze, no Play services, TTL/collapsing, OEM battery controls: record limits; never
  infer guaranteed delivery from a build or Robolectric success.
- Console does not send data-only. Use a trusted HTTP v1/Admin sender fixture later, keeping
  all sending credentials outside Android. No server is implemented in this phase.

## Initial baseline results (historical)

For the current destination-classification correction and its executed/unexecuted test
results, see [routing fix](ROUTING-FIX.ar.md). The following records the initial baseline.

Executed 2026-10-17 on JDK 17 / Gradle 8.14.3, with Messaging 25.0.1 in sample/test fixtures:

- 49 core contract checks passed.
- 13 Robolectric tests passed, zero skips/failures/errors (simulated API 28 and 33).
- Debug and Release SDK AARs built successfully.
- SDK instrumentation APK compiled/packaged; five device tests were NOT executed.
- Sample Java/resources/manifest compiled with openpushCompileOnly, without Firebase config.
- Lint completed with zero errors and five dependency-update warnings.
- SDK releaseRuntimeClasspath contains only core, without Firebase or a Firebase BoM.
- Sample merged manifest inspected: one host messaging service plus Firebase fallback,
  with the expected declared deep-link destination/filter.

The final combined Gradle verification exited 0 (BUILD SUCCESSFUL). HTML/XML reports are
linked in README. adb devices and emulator -list-avds returned no available devices/AVDs.
Real FCM delivery, process death, force-stop, OEM behavior, actual device navigation and
instrumentation execution remain PENDING, not passed. Other host Firebase versions require
their own compatibility checks. A real sample/google-services.json has since been supplied
and used for a configured APK build. Current practical testing adds eight sample integration
tests and APK credential-indicator scans; see docs/PRACTICAL-TEST-REPORT.ar.md and
verification/phase-gate.json for current evidence, the passed minimum Console gate and
remaining release limitations. Passing the minimum gate does not certify every acceptance case.
