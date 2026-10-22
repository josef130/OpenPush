# OpenPush v0.1 architecture

Status: initial implementation; Android build and device acceptance are required before release.

## Scope and plan

1. Define a versioned string-only routing envelope compatible with Console Custom Data.
2. Implement a dependency-free Java parser and tests.
3. Implement an Android adapter, immutable central routing registry and builder configuration.
4. Provide a runnable sample requiring the integrator's Firebase configuration.
5. Run available checks; keep device acceptance explicitly pending.

No server, dashboard, device registry, analytics or automatic token upload in this release.
Future Node.js/TypeScript senders and React UI must produce the same envelope. FCM remains
the Android transport; OpenPush is not a replacement for Google Play services.

## FCM lifecycle

- Foreground: notification, data and combined messages invoke onMessageReceived.
- Background: notification-only is displayed by FCM; combined is displayed by FCM and
  Custom Data arrives in launcher Activity intent extras only after a tap. Data-only invokes
  the service subject to delivery, priority and background restrictions.
- Process absent (ordinary OS kill): background rules apply; launch initializes Application
  before Activity. Swiping recents is not equivalent to force-stop, but OEM behavior varies.
- Force-stop in Settings: reopen the app manually before relying on delivery again.
- Console composer sends notification messages, optionally with Custom Data, not data-only.
- Notification-only messages open the app but have no OpenPush route.
- Delivery is not guaranteed: offline TTL, collapsing, Doze, permission, channel and OEM
  battery controls matter. Do not perform network work in onMessageReceived.

Sources checked 2026-10-17:
https://firebase.google.com/docs/cloud-messaging/android/receive-messages
https://firebase.google.com/docs/cloud-messaging/android/get-started
https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages
https://developer.android.com/about/versions/12/behavior-changes-12

## Integration and ownership

The library registers NO FirebaseMessagingService, Activity, receiver or automatic lifecycle
callback. Exactly one host service delegates to OpenPush.showNotification. Existing token,
deleted-message and non-OpenPush handlers remain owned by the host. The boolean return
means the message belongs to OpenPush, including malformed or suppressed notifications;
it prevents a second handler rendering the same message. Unrecognized messages return false.
For background Console messages the service is bypassed by FCM, so the host Activity MUST
call handleIntent in both onCreate and onNewIntent, after navigation is ready.

The Application creates the channel early. Runtime notification permission is requested by
the visible host Activity. Foreground presentation is explicit via the delegated method.
An immutable PendingIntent opens the host Activity directly, without a service/receiver
trampoline. Unique intent data per op_id prevents extras being overwritten between messages.

## Protocol v1

All values are strings, flat under FCM data / Console Custom Data:

```json
{"op_v":"1","op_id":"campaign-2026-001","op_type":"screen","op_target":"offers","op_arg_coupon":"WELCOME"}
```

- op_v: required exact supported version; unknown versions rejected.
- op_id: required unique per logical notification (1–128 ASCII letters/digits/._:-).
  Do not reuse across campaigns. Transport message IDs are not a substitute.
- op_type: screen, url, deeplink, or a registered custom action name. A syntactically valid
  unknown action can parse but cannot resolve or execute until explicitly registered.
  screen target is a host-defined allowlisted route name.
- op_target: required up to 2048 characters. url accepts HTTPS without credentials only;
  host configures exact approved domains via the builder. Android ACTION_VIEW/BROWSABLE
  opens approved HTTPS destinations. Optional stricter policies can be custom handlers.
- deeplink: absolute URI, approved scheme/host/path/component rule AND matching manifest
  filter. PackageManager resolution stays inside the host package; navigation explicitly
  targets the allowed Activity. Unsafe schemes, ambiguous components and traversal fail.
- op_arg_*: optional application arguments; unknown fields ignored for future extensions.
- op_title / op_body: optional local display fallback for future data-only messages.

No reflection-based Activity names, intent://, file://, javascript:, arbitrary Intent extras,
or remote Intent flags. The registered router handles approved links and the host RouteHandler
validates custom action/screen arguments. Built-in extras use io.openpush.arg.* and exclude
the original push envelope. Payloads are untrusted; authentication/authorization is rechecked at destination.
Do not put secrets in pushes. Unknown routes leave the launcher usable.

## Click semantics

Parse -> host acceptance -> persist claim -> invoke navigation. Host acceptance MUST have
no side effects. A bounded persistent 128-ID ledger survives Activity recreation and process
restart; an in-process lock serializes calls. Successful durable claim occurs before callback.
Duplicates within this window never dispatch twice. Failed persistence does not dispatch.
This is at-most-once dispatch, NOT exactly-once navigation: a crash between claim and callback
can lose navigation. Callback runtime failures return HANDLER_FAILED and are not retried. After eviction, data clear or reinstall,
the same ID may dispatch again. Single-process hosts only. Call on main thread.

## Dependencies and diagnostics

SDK Firebase Messaging is compileOnly 25.0.1. No Firebase BoM/runtime version is exported;
each host owns its Messaging declaration and validates its version. This avoids the prototype's
transitive BoM 35.0.1 upgrading unrelated host Firebase libraries. The sample and test fixtures
explicitly select Messaging 25.0.1. Existing host versions must not be changed merely to match
the sample. core is a separate Java module; source integration includes core and sdk. A raw
SDK AAR requires its core/Firebase dependencies and is not a fat AAR.

Diagnostics deliver fixed enum events only, without payloads, IDs, tokens, URLs or exception
messages. Default diagnostics are silent; sample debug logging is opt-in. Diagnostic callback
failures are contained. No token upload, service-account data or server API is implemented.

## Risks and release gates

FCM system-rendered notifications cannot be fully customized or deduplicated by this SDK;
their display happens without our code. Custom click_action targeting a different Activity
must also integrate handleIntent; the sample deliberately uses the launcher default.
Background suppression cannot be controlled through a service that FCM does not invoke.
No claim of OneSignal feature parity. No Maven publication until Android build, manifest
merge, lint and actual device matrix are complete. Dependency versions are pinned starting
points, not a claim that this combination was built here.

## Proposed and implemented tree

```text
build.gradle / settings.gradle / gradle.properties
gradlew / gradlew.bat / gradle/wrapper/
core/
  build.gradle
  src/main/java/io/openpush/core/Route.java
  src/main/java/io/openpush/core/RoutingEngine.java
  src/main/java/io/openpush/core/ClickLedger.java
  src/test/java/io/openpush/core/RouteTest.java
  src/test/java/io/openpush/core/InfrastructureTest.java
sdk/
  build.gradle
  src/main/AndroidManifest.xml
  src/main/java/io/openpush/sdk/OpenPush.java
  src/main/java/io/openpush/sdk/Router.java
  src/test/java/io/openpush/sdk/OpenPushTest.java
  src/androidTest/java/io/openpush/sdk/ClickTest.java
sample/
  build.gradle
  google-services.json                         supplied by integrator, ignored
  src/main/AndroidManifest.xml
  src/main/java/io/openpush/sample/
    SampleApplication.java
    MainActivity.java
    MessagingService.java
    ArticleActivity.java
  src/main/res/drawable/ic_notification.xml
docs/ARCHITECTURE.md                            design and failure semantics
docs/TESTING.md                                 checks and device acceptance matrix
scripts/test-core.ps1                           JDK-only contract test runner
README.md / LICENSE
```
