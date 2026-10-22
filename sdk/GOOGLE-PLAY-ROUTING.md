# Google Play and web click routing

The host keeps its existing configuration:

```java
.httpsHost("play.google.com")
.openUrlsInBrowser()
.preferGooglePlay()
```

For a validated Google Play app-details URL, the click router now constructs a separate ACTION_VIEW Intent with setPackage("com.android.vending"). It does not inherit CATEGORY_BROWSABLE or the APP_BROWSER selector from the web route. It never requests the advertised game's launcher. The invisible notification-click activity finishes after dispatch; the durable click ledger still prevents repeated execution.

Ordinary approved web URLs retain their existing BROWSABLE and optional APP_BROWSER selector behavior, including Android's browser chooser. If the official Store cannot be launched or resolution explicitly identifies another package, the existing HTTPS fallback and safe diagnostics remain in place. A successful startActivity call alone is not proof that the listing appeared.

Bare package names were already validated and normalized by Destination to a Google Play HTTPS details URL. This behavior and the op_v/op_id/op_type/op_target protocol are unchanged. No dashboard or server files are changed for this routing adjustment.

Reference: https://developer.android.com/distribute/marketing-tools/linking-to-google-play#android-app

The added bridge regression test checks the full listing URL, explicit Store package, absence of browser selector/category and host component, and no second launch on repeated tap. Existing tests cover web routing, package normalization, Store unavailable/denied, invalid targets and duplicate suppression. These are automated Android simulations; real-device listing visibility must be verified separately. No Android device was reported by adb devices -l at the start of this change (2026-10-21).

Verification on 2026-10-21: SDK 37 tests passed, zero failures, including the new full-URL bridge test. The combined core:test, sdk:testDebugUnitTest, sdk:assembleDebug and sample:assembleDebug invocation finished BUILD SUCCESSFUL. A redundant concurrent SDK test invocation failed on a Windows output.bin file lock; its outcome is not counted as a test success. The successful invocation generated all 37 SDK XML test results. Artifacts: sdk/build/outputs/aar/sdk-debug.aar and sample/build/outputs/apk/debug/sample-debug.apk. No real device install or visible Google Play listing was verified in this run.

## Subsequent real-device verification, 2026-10-21

The updated APK was installed with adb install -r on RMX3852. Dashboard message e9cfc4ef-e808-467d-acdc-8050ef35fb75 was sent to the single registered test phone and its exact title was observed in Android's posted notifications. Its ID was subsequently present in the SDK click ledger. The user tapped it and reported that the browser chooser still appeared: the direct Store criterion therefore FAILED on this device.

Outside the SDK, `am start -W -a android.intent.action.VIEW -d 'https://play.google.com/store/apps/details?id=com.chikh.nano' -p com.android.vending` reported com.daemon.shelper/.intercept.ost.MarketDeepLinkHandlerActivity, not the official Store activity. Package resolution returned that same intermediary. This supports the device interception plus existing Store-mismatch/HTTPS fallback explanation; the filtered Logcat output did not provide a launch trace, so it is not presented as an observed SDK diagnostic event. No device protection or default-handler settings were changed. No second device comparison was available.

Evidence: verification/play-direct-device-20261021.json and verification/play-direct-20261021-notification.json (paths relative to repository root).

## Resolver diagnosis and successful manual verification

Further inspection found that resolveActivity named the OEM intermediary while queryIntentActivities for the identical package-limited HTTPS Intent returned an enabled Google Play listing handler. Launching that listed activity explicitly displayed the game's page inside com.android.vending. This exposed an SDK issue in addition to OEM routing: the mismatch guard treated a differing default resolver as conclusive proof that no official Store handler could run, immediately choosing browser fallback.

Router now handles this specific mismatch by querying matching activities and choosing only a unique exported, enabled activity belonging to com.android.vending, with an enabled application and no ungranted required permission. It sets that verified component on the existing package-constrained ACTION_VIEW Intent. The activity class is discovered at runtime, never hardcoded or supplied remotely. Ambiguous, private, unavailable or denied handlers retain the previous safe fallback. Ordinary web routes are unchanged. No device protection, default handler or package was disabled.

SDK tests: 39 passed, zero failures. SDK AAR and sample APK builds passed. The updated APK was installed without clearing data. New Dashboard message dee49a42-38e6-4705-846e-dd8be8123254 (OpenPush Verified Play Handler 02) was observed in Android notifications. The tool tapped its exact visible title and confirmed its ID in the click ledger. The UI dump two seconds later showed the launcher, so that dump alone does not establish listing visibility. The user then explicitly confirmed the fix succeeded. Direct Store opening is therefore manually confirmed on this phone; continuous automated observation of the complete transition was not captured.
