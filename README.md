# OpenPush Android SDK

## Android SDK

Automatic integration is implemented locally (not added to the published 0.1.0 tag).
See [three-step setup](docs/AUTOMATIC-SETUP.md): configure Firebase, add the generated
`openpush.xml` and notification icon, then call `OpenPush.init(this)` in your Activity.
New apps need no custom Application or FirebaseMessagingService. Existing services
must delegate explicitly; the manual builder API below remains supported.

Java Android library for Firebase Console notification clicks, central routing rules,
web URLs, Google Play links/package names, declared deep links and custom actions. The Android modules contain no sending keys or service-account credentials. MIT licensed. API 24+, compileSdk 35, Java 8 source.
This checkout builds with JDK 17, Gradle 8.14.3 and AGP 8.10.1.

## Add modules and configure Firebase

Include `:core` and `:sdk` in Gradle settings and google()/mavenCentral() repositories.
Add these dependencies to your app:

```groovy
dependencies {
    implementation project(':sdk')
    implementation 'com.google.firebase:firebase-messaging:25.0.1'
}
```

Keep the host's root Gradle/plugin configuration. If its library plugin needs declaring,
use the SAME Android Gradle Plugin version as that host's application plugin; do not copy
this checkout's root build.gradle over an existing project. These versions describe this
checkout's verified build, not a mandatory upgrade of another app's toolchain.

The SDK now exports **Firebase Messaging 25.0.1** as a runtime API dependency, required
by its merged fallback service. It exports no Firebase BoM. If your app declares Messaging
through a BoM, **keep that existing declaration**: do not add a second version or replace
its BoM. This example is not an instruction to upgrade/downgrade existing apps. Other
versions require host-specific validation; universal compatibility is not claimed.
Inspect the resolved Firebase graph in each host; Gradle may select a higher compatible version.
The independent sample explicitly uses 25.0.1 instead of the previous prototype's
BoM-selected 26.0.0, to exercise the lower compile baseline without changing other apps.
See [Firebase releases](https://firebase.google.com/support/release-notes/android#messaging_v25-0-1).

These are source modules; no public Maven artifact is published. A raw SDK AAR is not a
bundle of core and Firebase. Include both modules, or eventually publish with a dependency
POM; do not drop the AAR alone into libs and expect its dependencies to be embedded.

For EACH host register its exact applicationId/flavor in its own Firebase project. Place
its real `google-services.json` in the APP module. Apply the Google Services plugin there
(this checkout declares 4.5.0 in the root); existing hosts keep their compatible plugin
version. No JSON/plugin belongs in the SDK module. FirebaseApp configuration is mandatory;
JSON + plugin is the supported automatic setup in phase one. Client JSON contains identifiers,
not an Admin private key. Never embed server keys or service-account credentials in the APK.

INTERNET and POST_NOTIFICATIONS merge from the library manifest. On API 33+ request notification
permission from a visible Activity; the SDK never prompts from a service. No foreground-service
or storage permission is needed. FCM merges its own transport permissions as usual. Use a Google
Play services device/emulator, open the app once and obtain its FCM token. Analytics and SHA
fingerprints are not mandatory for basic FCM.
[Android Firebase setup](https://firebase.google.com/docs/android/setup),
[FCM setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).

## Configure routing once in Application

```java
push = OpenPush.builder(this, MainActivity.class, R.drawable.ic_notification)
    .httpsHost("example.com")
    .httpsHost("www.google.com") // Sample test destination; hosts approve their own exact domains.
    .httpsHost("google.com")
    .httpsHost("play.google.com")
    .openUrlsInBrowser() // Keep browser choice for web URLs and Store HTTPS fallback.
    .preferGooglePlay() // Valid Play listings/package names try the official Store first.
    .httpHost("example.com") // Only if this host should also accept plaintext HTTP links.
    .deepLink("openpush", "article", "/read", ArticleActivity.class)
    .screen("offers", new OpenPush.RouteHandler() {
        public boolean accepts(Route route) { return true; }
        public void open(Activity host, Route route) {
            ((MainActivity) host).showOffers(route);
        }
    })
    .action("article", new OpenPush.RouteHandler() {
        public boolean accepts(Route route) {
            String id = route.arguments.get("article_id");
            return id != null && id.matches("[0-9]{1,12}");
        }
        public void open(Activity host, Route route) {
            host.startActivity(new Intent(host, ArticleActivity.class)
                .putExtra(Router.ARGUMENT_PREFIX + "article_id", route.arguments.get("article_id")));
        }
    })
    .diagnostics(event -> { if (BuildConfig.DEBUG) Log.d("OpenPush", event.name()); })
    .build();
push.createChannel("Notifications");
```

The builder produces an immutable registry. Duplicate/reserved action registrations fail
explicitly. `accepts` must be PURE: validate arguments, authorization and readiness without
navigation or side effects. A rejected route is not claimed; the host can retry after login.
Handlers receive immutable Route.arguments. Do not capture an Activity in a long-lived handler.
See the complete Java sample in sample/src/main/java/io/openpush/sample/.

Diagnostics are opt-in fixed enum events, with NO tokens, IDs, URLs, argument values, raw
payloads or exception messages. A failing diagnostics callback cannot break routing.
No analytics or token upload is performed. The sample displays a test token for Console
testing only; production apps must not copy that test UI or log registration tokens.

`op_type=url` now classifies three inputs: HTTP/HTTPS web URLs, Google Play listing URLs,
and syntactically valid ASCII Java package names such as `com.chikh.nano`. Package names
normalize to `https://play.google.com/store/apps/details?id=com.chikh.nano`. The same parser
runs before local presentation and again at click time. Store links require one valid `id`;
duplicate/empty IDs, userinfo, encoded paths and fragments are rejected. Normalized Store
links retain only the app ID. Package syntax validation does not prove a listing exists.
Package/Store routes still require `.httpsHost("play.google.com")`. Ordinary HTTP needs
its own `.httpHost(...)` approval; existing HTTPS approvals do not silently permit HTTP.

Optional `.launchDiagnostics(report -> ...)` reports action, scheme, requested package,
resolved component and REQUESTED/RETURNED/NOT_FOUND/DENIED/FAILED. It never receives the
full URI, query, message ID, extras or exception text. RETURNED means startActivity returned,
not that the destination appeared. Resolver metadata can be null under package visibility
limits. SDK manifest queries are limited to web VIEW handlers and the official Store.
The sample enables BuildConfig generation for its debug logger; hosts can omit diagnostics
or use their existing debug flag without enabling a new build feature.

`.openUrlsInBrowser()` keeps ACTION_VIEW and the approved URL, with a selector
using ACTION_MAIN + CATEGORY_APP_BROWSER. Android selects a browser or presents
browser choices. With `.preferGooglePlay()`, validated Play listings and package names
try ACTION_VIEW with the canonical HTTPS listing and package `com.android.vending`
FIRST, without a browser selector. Other web URLs keep their existing browser choice.
If the Store launch throws, the same approved HTTPS listing uses the browser policy.
If package-constrained resolution reports a different package, the SDK records
STORE_HANDLER_MISMATCH and avoids knowingly opening a different market. A null
resolver from package-visibility limits does not suppress the actual native attempt.
STORE_FALLBACK explicitly marks the unsuccessful direct Store path. RETURNED/OPENED
still do not prove a visible Store page. Missing both handlers returns HANDLER_FAILED.
Validation, exact host approval, durable op_id claims and existing deep links remain unchanged.
Using `.openUrlsInBrowser()` ALONE still opens Play listings in a browser; using
`.preferGooglePlay()` ALONE keeps generic HTTPS fallback as before. The two options
now compose; the earlier mutually-exclusive restriction has been removed.
See [Android Intent selectors](https://developer.android.com/reference/android/content/Intent#setSelector(android.content.Intent)).

Optional `.pipelineDiagnostics(report -> ...)` traces RECEIVED, PENDING_INTENT, CLICK,
RESOLVED, CLAIM and DISPATCH. It reports bounded/sanitized key names, destination kind,
decision and process-salted fingerprints of the input, normalized target and op_id.
Launch reports include the matching destination fingerprint. No values, URL, token,
argument or ID are logged. Compare fingerprints only within the SAME process; a cold
process deliberately changes the salt. The debug sample logs this under OpenPushPipeline.
FCM's background Notification+Data presentation bypasses showNotification, so its first
SDK trace is CLICK, not RECEIVED. Missing callbacks in that case are expected.

## Keep one host FirebaseMessagingService

OpenPush registers NO host service. Preserve your existing service, including onNewToken,
onDeletedMessages and business handling, and delegate from its callback:

```java
@Override public void onMessageReceived(RemoteMessage message) {
    // Existing universal receipt processing may run before delegation.
    if (push.showNotification(message)) return;
    // Your existing handling for non-OpenPush messages remains here.
}
```

True means owned (op_v present), even when malformed, suppressed or permission-denied; do
not display it again in another handler. False leaves the message with your existing code.
Without a host service, add one like the sample, non-exported, with the action
`com.google.firebase.MESSAGING_EVENT`. Never register competing independent host services.
Firebase's own lower-priority fallback service is normal. Inspect the merged manifest,
especially if another notification SDK is installed.

The delegated method displays locally and never navigates on receipt. Without op_v, foreground
messages remain host-owned; the sample's HostMessageHandler displays ordinary notification
messages and preserves unrelated data handling. Do long-running business work
using the host's WorkManager flow rather than in this callback.

## Forward clicks from the launcher Activity

Call after UI/navigation initialization, on the MAIN thread:

```java
@Override public void onCreate(Bundle state) {
    super.onCreate(state);
    // Create/restore UI first.
    push.handleIntent(this, getIntent());
}
@Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    push.handleIntent(this, intent);
}
```

Integrate the actual launcher, including any splash entry point. Preserve/forward its original
Intent when deferring navigation until readiness/login. Use singleTop or an equivalent existing
launch policy and validate your host back stack. Do not skip handling solely because
savedInstanceState exists; restore UI state separately. The sample demonstrates this.

These hooks cannot be replaced with service-only integration: background Console messages
bypass the host service, and onNewIntent is not an Application lifecycle callback. Custom
click_action entry Activities must also forward clicks; avoid custom click_action in phase one.
The old constructor and handleIntent(host, intent, handler) remain supported.

ClickResult is IGNORED, REJECTED, DUPLICATE, STORAGE_FAILED, OPENED or HANDLER_FAILED. Handler
runtime exceptions are contained. A failed handler after claiming is not retried. Main-thread
API misuse fails explicitly. Diagnostics supply only fixed event names.

## HTTPS and declared Deep Links

The `url` action uses ACTION_VIEW + CATEGORY_BROWSABLE after HTTPS/authority validation and
exact hostname approval. Android selects a browser or verified App Link handler. Missing handlers
become HANDLER_FAILED without crashing. No credentials or nonstandard HTTPS ports are accepted.

The `deeplink` action is scoped to the host package. It requires BOTH a registered
scheme/host/path/component rule AND a matching DEFAULT/BROWSABLE manifest Intent Filter on that
Activity. The SDK explicitly targets the resolved allowed component. Ambiguous allowed components,
undeclared/disabled destinations, unsafe schemes and path traversal are rejected. A path rule matches
exactly or at a slash boundary: /read never approves /reader. Declare supported destinations:

```xml
<activity android:name=".ArticleActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="openpush" android:host="article" android:pathPrefix="/read/" />
    </intent-filter>
</activity>
```

Use exported=true only when the destination should accept external deep links; package-local
OpenPush routing can use an unexported Activity with the same filter. Exported destinations
must validate their incoming URI/extras too: they can be launched outside
OpenPush. HTTPS App Links need normal domain verification/assetlinks configuration. OpenPush does
not manufacture that association. [Android deep-link guide](https://developer.android.com/training/app-links/create-deeplinks).

Built-in URL/deep-link actions forward op_arg_* as String extras named `io.openpush.arg.<name>`,
without forwarding the push envelope, which avoids redispatch loops. Browser apps may ignore extras;
put web query parameters in the HTTPS URL itself. Custom handlers receive Route.arguments directly.

## Firebase Console: Notification + Custom Data

Run the configured sample, enable notifications, copy its token and use Messaging composer
with title/body and the test device. Add Custom Data as individual string fields; use the full
composer campaign flow if the quick test dialog does not expose them:

```json
{"op_v":"1","op_id":"demo-001","op_type":"screen","op_target":"offers","op_arg_coupon":"WELCOME"}
```

Other supported sample payloads:

```json
{"op_v":"1","op_id":"demo-002","op_type":"url","op_target":"https://example.com/article/42"}
{"op_v":"1","op_id":"demo-003","op_type":"deeplink","op_target":"openpush://article/read/42","op_arg_article_id":"42","op_arg_level":"3"}
{"op_v":"1","op_id":"demo-004","op_type":"article","op_target":"reader","op_arg_article_id":"42"}
```

Use a NEW op_id per logical notification. All values are strings. op_id is mandatory and accepts
1–128 ASCII letters/digits/._:-; op_v must be 1; op_target is required, at most 2048 characters.
Up to 32 op_arg_* arguments, values at most 2048 characters; the TOTAL envelope must also fit
FCM transport limits. Custom action names match [a-z][a-z0-9_]{0,63}. Unknown actions are parsed
but cannot execute unless registered. Missing data, unknown versions and malformed links never
navigate. No reflection, remote class names, intent:// execution or arbitrary Intent flags.

When retesting in Firebase Console, changing only op_target is insufficient: also change
op_id. Reusing a previously clicked ID opens the launcher gateway, then returns DUPLICATE
without launching another destination. This is deliberate replay protection, not a URL
fallback. The current protocol uses op_target with op_type=url and op_v=1; standalone
url/link keys are not routing aliases. Ordinary notifications without that envelope retain
the host's ordinary launcher behavior. Without op_v the click is IGNORED and the
message stays host-owned; with op_v but an invalid/incomplete envelope it is rejected.

For a Google test supply ALL four Custom Data rows: `op_v=1`, a fresh
`op_id=google-test-002`, `op_type=url`, `op_target=https://www.google.com`.
`op_id` is not the URL and `url` is not a key alias. The sample now approves
`www.google.com`, `google.com` and `play.google.com` explicitly; older installed APKs
without those rules reject them even with a valid envelope. Rebuild/reinstall the
Firebase-configured sample before testing these hosts. This retains exact host
approval: other domains and subdomains are not automatically allowed.

Configure FCM default monochrome small-icon/channel metadata as in the sample. OpenPush does not
overwrite host manifest metadata or existing user channel settings. Initialize the channel early.

## Lifecycle and duplicate guarantees

Foreground: all message types invoke onMessageReceived; delegated OpenPush messages display locally.
Background/ordinary process death: notification-only and notification+data display through FCM;
Custom Data arrives in launcher extras ON TAP, bypassing the host service. Console sends notification
messages, optionally with data, not data-only. Future data-only senders invoke the service subject
to delivery/priority/background restrictions. Force-stop in Settings requires reopening the app.
Recents swipe is different from force-stop; OEM behavior varies. Offline, TTL, collapsing, Doze and
notification settings can delay/prevent delivery. FCM-rendered background notifications cannot be
fully controlled/deduplicated by this SDK.
[Official FCM behavior](https://firebase.google.com/docs/cloud-messaging/android/receive-messages).

A durable ledger claims the last 128 IDs BEFORE dispatch, shared by adapters in one process.
Repeated lifecycle callbacks cannot dispatch a retained ID again. A crash after claim but before
navigation can lose navigation: this is at-most-once dispatch, not exactly-once completion.
Exceptions after claiming are not retried. Eviction, data clear, reinstall and backup behavior
can affect retained IDs. Multi-process hosts are unsupported. Repeated local notification IDs
replace the displayed notification but may redisplay after an earlier dismissal.

## Build and verification

Set JAVA_HOME to JDK 17 and ANDROID_HOME to SDK 35. Use the checksum-pinned wrapper:

```text
gradlew.bat :core:contractTest :sdk:assembleRelease :sdk:testDebugUnitTest :sdk:lintDebug
gradlew.bat -PopenpushCompileOnly :sample:compileDebugJavaWithJavac
gradlew.bat :sdk:connectedDebugAndroidTest
```

The second command omits Google Services. The equivalent -PopenpushLocalOnly mode can now
build a runnable local QA APK; its UI explicitly disables FCM token requests and labels local
fixtures as simulations. Neither mode proves Firebase delivery. Normal builds require the real app JSON.
The third command requires a device/emulator. See [testing and release gates](docs/TESTING.md).

### Native Play plus web browser policy — 2026-10-17

- Core: **76 contract checks passed**.
- SDK Robolectric: **28 tests passed**, zero failures/errors/skips after correcting
  one OEM test fixture (the initial failed run is retained in verification).
- Sample Robolectric: **11 tests passed**. The sample now expects Play URL/package
  routes in the Store and normal web routes through the existing browser selector.
- Release AAR: **built**. Current phone/FCM and APK results are recorded in
  [Native Play report](docs/NATIVE-PLAY.ar.md); native Store visibility is not certified.
- The current phone still redirects a package-constrained Store request outside
  the SDK to an OEM intermediary/market. This is not a passing Store-page test.

### Historical browser-only results — 2026-10-17

- SDK Robolectric: **25 tests passed**, zero failures/errors/skips.
- Sample Robolectric: **11 tests passed**, including Google and Play pages through
  independent notification clicks with browser selectors, and incomplete payloads.
- Firebase-configured APK and release SDK AAR: **built**; APK installed on the test phone.
- Lint: **zero errors, five dependency-update warnings**.
- Real Console Google notification click: correct envelope parsed, one browser-selector
  launch requested, Android ResolverActivity selected. Visible page success is **pending**.
- Real Play-page browser test and complete lifecycle/coexistence matrix: **not completed**.

Historical report: [Custom Data/browser investigation](docs/CUSTOM-DATA-ROUTING.ar.md).
Native Google Play testing was deferred at that stage. Earlier native Store failures
remain historical evidence, not proof that the new browser mode succeeds or fails.

### Historical baseline before browser mode — 2026-10-17

- Core contract runner: **76 checks passed**, including destination classification, routing registry and failed storage.
- Robolectric SDK: **20 tests passed, zero skipped/failures/errors**, on simulated API 28 and 33.
  Includes Android 13 notification permission denial/grant, channel disablement, independent
  click payloads, missing browser, safe failures, approved HTTPS/deep-link rules and deduplication.
- Debug and Release SDK AAR builds: **passed**.
- SDK instrumentation: **five tests passed on a physical Android 14 phone**, using the
  sample test harness after the OEM rejected the standalone library test APK.
- Sample integration: **nine Robolectric tests passed**, including Play HTTPS/package
  normalization to the same native Store Intent. Three additional local-input
  acceptance tests passed on real Android (notification coexistence/host behavior,
  Activity routing/replay/invalid targets, and destination classification); eight selected
  physical-device contracts passed overall. These local contracts do not prove FCM transport.
- Sample Java compilation, resource processing and manifest merge: **passed** in explicit
  compile-only mode, without a real Firebase configuration.
- Lint: **zero errors, five dependency-update warnings**. Baseline/test versions remain pinned
  deliberately; none of these warnings justifies changing a host's Firebase versions blindly.
- releaseRuntimeClasspath inspection: **core only**, no Firebase runtime dependency or BoM.
- Merged sample manifest inspected: one host messaging service, Firebase's lower-priority
  fallback, and the declared ArticleActivity deep-link filter.

Reports: [Robolectric HTML](sdk/build/reports/tests/testDebugUnitTest/index.html),
[JUnit XML](sdk/build/test-results/testDebugUnitTest/TEST-io.openpush.sdk.OpenPushTest.xml),
[Lint HTML](sdk/build/reports/lint-results-debug.html).
Build outputs: sdk/build/outputs/aar/sdk-debug.aar and sdk-release.aar (dependencies are not embedded).

**Real Firebase/device results:** Console notification-only and notification+data delivery,
foreground host callback versus background system display, permission grant, channel creation,
and ordinary process-kill notification delivery were observed on RMX3852 / API 34.
The user confirmed a real tray tap opened HTTPS A. Actual Firebase-created PendingIntents
opened exact A4 and B4 URLs in Opera, verified automatically (direct dispatch, not a tray gesture).
A real tray-tap test opened Article 42 / Level 3. Earlier automated HTTPS tray gestures failed
and remain recorded; they are not counted as passes. APK static scanning found zero configured
server-secret indicators across 128 ZIP entries, with stated detection limits.
**Not verified:** real data-only transport, Settings force-stop/recents swipe, other Android
versions/Firebase host versions, verified App Links and OS permission/channel revocation.
See the current [practical report](docs/PRACTICAL-TEST-REPORT.ar.md),
[Firebase setup runbook](docs/FIREBASE-PRACTICAL-SETUP.ar.md) and
the historical phase gate retained in the private dashboard archive. No server or dashboard has been started.

For the latest image/Play-link delivery investigation and exact Console payloads, see
[Play Store historical test report (Arabic)](docs/PLAY-STORE-TEST.ar.md). The corrected SDK
now normalizes bare package names as described above; see [current routing fix](docs/ROUTING-FIX.ar.md).
For native Store routing use `.httpsHost("play.google.com").preferGooglePlay()`.
This opt-in targets `com.android.vending` for approved Play HTTPS links and falls back to
the same approved HTTPS URL when Google Play is unavailable. It does not approve a host
by itself, and does not alter other HTTPS routing or the interpretation of package names.


تفاصيل إعداد قياس الاستلام والعرض والنقر، ودقة CTR وحدود FCM: [إحصائيات SDK](docs/SDK-TELEMETRY.ar.md).

تفاصيل المسار المباشر والاختبارات وحدود جهاز Google Play الحالي: [التوجيه من الإشعارات](docs/DIRECT-NOTIFICATION-ROUTING.ar.md).

### Notifications after removing the app from recents

On the tested realme RMX3852, disabling **App info → Battery usage → Allow background activity** caused task removal to mark OpenPush stopped. Enabling that per-app option restored real FCM notification posting with no application process, including a screen-off test. See the [physical-device evidence and limits](docs/ANDROID-BACKGROUND-DELIVERY.ar.md). This does not override Android Force stop.

تفاصيل MAU والعدادات والهوية والشهر المعتمد: [عدادات التطبيقات](docs/APP-COUNTERS-MAU.ar.md).



## Independent checkout
See [local setup](LOCAL-SETUP.md). The dashboard and private historical verification files are kept outside this SDK directory. No Git repository has been created or published.


The historical lint report link is generated only by running :sdk:lintDebug; it is not committed.

