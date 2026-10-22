# Web URL notification clicks

Ordinary approved HTTP/HTTPS targets now use `new Intent(Intent.ACTION_VIEW, Uri.parse(route.target))` (plus existing route argument extras). No browser package, explicit component, browser selector, or chooser is assigned. Android chooses the default or verified URL handler. If no suitable default exists, Android may show a resolver; SDK does not suppress or bypass that choice. A verified App Link can also resolve to its approved application.

The prior `.openUrlsInBrowser()` setting added an ACTION_MAIN / CATEGORY_APP_BROWSER selector to ordinary web links. That selector is now limited to its existing Google Play HTTPS fallback behavior, so an existing host using this builder method receives the new ordinary-Web-URL behavior without an integration change. The public builder method remains available.

Google Play native routing, verified official-handler selection on OEM resolver mismatch, package-name normalization, Store fallback behavior, click deduplication, unique PendingIntent data, and the op_v/op_id/op_type/op_target protocol are unchanged. No dashboard, database or server files are changed for this adjustment.

Reference: https://developer.android.com/guide/components/intents-common#Browser

Automated contracts cover HTTPS and opted-in HTTP, no selector/package/component on ordinary web requests, unchanged Store-specific selector fallback, native Play and exported-handler repair, notification-click bridge routing without launching the host, and duplicate click suppression.

Real-device acceptance still requires all of: installing this build, tapping a new Web URL notification with an existing default browser, repeating without a default (recording Android's decision), tapping a Google Play notification, tapping a notification without a destination, and reviewing app-scoped Logcat. Intent simulation alone does not establish that a chooser disappeared. adb devices -l reported no connected device when this change started on 2026-10-21; do not treat real-device cases as passed until they are actually executed.

2026-10-21 validation: SDK unit suite passed (39 tests, zero failures/errors); SDK AAR and sample debug APK built successfully. adb detected RMX3852 and install -r succeeded. Browser role holder was com.opera.browser. A new Dashboard test (OpenPush Web Default QA 01) was accepted by FCM, but its notification was not found on the phone during inspection; no tap result was verified. Default-browser/no-default, Google Play regression and no-destination physical tap cases remain unverified for this build. No default-browser settings were changed. This is not evidence that the chooser disappeared on the device.
