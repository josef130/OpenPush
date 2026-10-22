package io.openpush.sdk;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import com.google.firebase.messaging.RemoteMessage;
import io.openpush.core.Route;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class OpenPushTest {
    private Context context;
    private Activity host;
    private int opens;
    private List<OpenPush.Event> events;
    private OpenPush push;
    public static class ArticleActivity extends Activity { }
    public static class NoBrowserActivity extends Activity {
        @Override public void startActivity(Intent intent) {
            throw new android.content.ActivityNotFoundException("Private test exception text");
        }
    }
    public static class NoStoreActivity extends Activity {
        int nativeAttempts;
        @Override public void startActivity(Intent intent) {
            if ("com.android.vending".equals(intent.getPackage())) {
                nativeAttempts++;
                throw new android.content.ActivityNotFoundException("No Store fixture");
            }
            super.startActivity(intent);
        }
    }
    public static class DeniedStoreActivity extends Activity {
        @Override public void startActivity(Intent intent) {
            if ("com.android.vending".equals(intent.getPackage())) throw new SecurityException("must never be logged");
            super.startActivity(intent);
        }
    }
    /** Scoped OEM fixture: stock Android correctly enforces setPackage, this phone does not. */
    @org.robolectric.annotation.Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
    public static class InterceptingPackageManager extends org.robolectric.shadows.ShadowApplicationPackageManager {
        @Override @org.robolectric.annotation.Implementation
        protected ResolveInfo resolveActivity(Intent intent, int flags) {
            if ("com.android.vending".equals(intent.getPackage())) {
                ResolveInfo wrong = new ResolveInfo();
                wrong.activityInfo = new ActivityInfo();
                wrong.activityInfo.packageName = "com.example.intermediary";
                wrong.activityInfo.name = "com.example.intermediary.MarketActivity";
                return wrong;
            }
            return super.resolveActivity(intent, flags);
        }
    }
    private final OpenPush.RouteHandler offers = new OpenPush.RouteHandler() {
        public boolean accepts(Route route) { return true; }
        public void open(Activity activity, Route route) { opens++; }
    };

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("openpush.clicks.v1", Context.MODE_PRIVATE).edit().clear().commit();
        host = Robolectric.buildActivity(Activity.class).setup().get();
        events = new ArrayList<>();
        opens = 0;
        push = builder().build();
    }
    private OpenPush.Builder builder() {
        return OpenPush.builder(context, Activity.class, android.R.drawable.ic_dialog_info)
                .screen("offers", offers).httpsHost("example.com").diagnostics(events::add);
    }
    private Intent click(String id, String type, String target) {
        return new Intent().putExtra("op_v", "1").putExtra("op_id", id).putExtra("op_type", type)
                .putExtra("op_target", target).putExtra("op_arg_article_id", "42");
    }

    @Test public void routesOnlyOnClickAndDeduplicatesAcrossAdapters() {
        Intent intent = click("a", "screen", "offers");
        assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(host, intent));
        assertEquals(OpenPush.ClickResult.DUPLICATE, builder().build().handleIntent(host, intent));
        assertEquals(1, opens);
        assertTrue(events.contains(OpenPush.Event.DUPLICATE));
    }

    @Test public void rejectsUnknownActionAndInvalidPayloadWithoutClaimingId() {
        assertEquals(OpenPush.ClickResult.REJECTED, push.handleIntent(host, click("a", "unknown", "x")));
        assertEquals(OpenPush.ClickResult.REJECTED, push.handleIntent(host, click("a", "screen", "missing")));
        assertEquals(OpenPush.ClickResult.REJECTED, push.handleIntent(host, click("a", "screen", "offers").putExtra("op_id", 42)));
        assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(host, click("a", "screen", "offers")));
        assertEquals(OpenPush.ClickResult.IGNORED, push.handleIntent(host, new Intent()));
        assertEquals(1, opens);
    }

    @Test public void opensOnlyAllowlistedHttpsAndPreservesArguments() {
        assertEquals(OpenPush.ClickResult.REJECTED, push.handleIntent(host, click("a", "url", "https://evil.example/")));
        assertEquals(OpenPush.ClickResult.REJECTED, push.handleIntent(host, click("a", "url", "javascript:alert(1)")));
        assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(host, click("a", "url", "https://example.com/article/42")));
        Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, opened.getAction());
        assertFalse(opened.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertNull(opened.getSelector());
        assertEquals("42", opened.getStringExtra(Router.ARGUMENT_PREFIX + "article_id"));
        assertFalse(opened.hasExtra("op_v"));
    }

    @Test public void customActionReceivesImmutableArgumentsAndFailsSafely() {
        OpenPush configured = builder().action("article", new OpenPush.RouteHandler() {
            public boolean accepts(Route route) { return route.arguments.containsKey("article_id"); }
            public void open(Activity activity, Route route) {
                assertEquals("42", route.arguments.get("article_id"));
                throw new IllegalStateException("sensitive message must never reach diagnostics");
            }
        }).build();
        assertEquals(OpenPush.ClickResult.HANDLER_FAILED, configured.handleIntent(host, click("a", "article", "reader")));
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(host, click("a", "article", "reader")));
        assertTrue(events.contains(OpenPush.Event.HANDLER_FAILED));
    }

    @Test public void nativePlayPreferenceStillRequiresHostApprovalAndKeepsOtherUrlsGeneric() {
        Intent play = click("play", "url", "https://play.google.com/store/apps/details?id=com.chikh.nano");
        assertEquals(OpenPush.ClickResult.REJECTED, builder().preferGooglePlay().build().handleIntent(host, play));
        OpenPush configured = builder().httpsHost("play.google.com").preferGooglePlay().build();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, play));
        Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals("com.android.vending", opened.getPackage());
        assertEquals(play.getStringExtra("op_target"), opened.getDataString());
        assertEquals("42", opened.getStringExtra(Router.ARGUMENT_PREFIX + "article_id"));
        assertFalse(opened.hasExtra("op_v"));
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(host, play));
        assertNull(Shadows.shadowOf(host).getNextStartedActivity());
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click("web", "url", "https://example.com/")));
        assertNull(Shadows.shadowOf(host).getNextStartedActivity().getPackage());
    }

    @Test public void webUrlsUseSystemDefaultsWhileLegacyStoreBrowserPolicyIsPreserved() {
        List<Router.LaunchReport> launches = new ArrayList<>();
        OpenPush configured = builder().httpsHost("www.google.com").httpsHost("play.google.com").httpHost("example.com")
                .openUrlsInBrowser().launchDiagnostics(launches::add).build();
        String[] urls = {"https://www.google.com", "https://play.google.com/store/apps/details?id=com.chikh.nano", "http://example.com/read/42"};
        for (int i = 0; i < urls.length; i++) {
            Intent tap = click("browser-" + i, "url", urls[i]);
            assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, tap));
            Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
            assertEquals(Intent.ACTION_VIEW, opened.getAction());
            assertEquals(urls[i], opened.getDataString());
            boolean storeFallback = i == 1;
            assertEquals(storeFallback, opened.hasCategory(Intent.CATEGORY_BROWSABLE));
            assertNull(opened.getPackage());
            assertNull(opened.getComponent());
            if (storeFallback) {
                assertEquals(Intent.ACTION_MAIN, opened.getSelector().getAction());
                assertTrue(opened.getSelector().hasCategory(Intent.CATEGORY_APP_BROWSER));
            } else assertNull(opened.getSelector());
            assertEquals(storeFallback, launches.get(i * 2).browserSelector);
            assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(host, tap));
            assertNull(Shadows.shadowOf(host).getNextStartedActivity());
        }
        assertEquals(urls.length * 2, launches.size());
        assertEquals(OpenPush.ClickResult.REJECTED, configured.handleIntent(host,
                click("foreign-browser-host", "url", "https://foreign.example/")));
    }

    @Test public void missingBrowserIsContainedWithoutOpeningNativeStore() {
        List<Router.LaunchReport> launches = new ArrayList<>();
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser()
                .launchDiagnostics(launches::add).build();
        Activity unavailable = Robolectric.buildActivity(NoBrowserActivity.class).setup().get();
        Intent tap = click("no-browser-mode", "url", "com.chikh.nano");
        assertEquals(OpenPush.ClickResult.HANDLER_FAILED, configured.handleIntent(unavailable, tap));
        assertEquals(2, launches.size());
        assertEquals("NOT_FOUND", launches.get(1).outcome);
        assertNull(launches.get(0).requestedPackage);
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(unavailable, tap));
    }

    @Test public void nativePlayAndWebBrowserPoliciesComposeWithoutChangingWebRouting() {
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay().build();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click("mixed-play", "url", "com.chikh.nano")));
        Intent store = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals("com.android.vending", store.getPackage());
        assertEquals(Intent.ACTION_VIEW, store.getAction());
        assertFalse(store.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertNull(store.getComponent());
        assertNull(store.getSelector());
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano", store.getDataString());
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click("mixed-web", "url", "https://example.com/")));
        Intent web = Shadows.shadowOf(host).getNextStartedActivity();
        assertNull(web.getPackage());
        assertFalse(web.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertEquals(Intent.ACTION_VIEW, web.getAction());
        assertNull(web.getSelector());
    }

    @Test public void playPreferenceIsOptIn() {
        OpenPush configured = builder().httpsHost("play.google.com").build();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host,
                click("play", "url", "https://play.google.com/store/apps/details?id=com.chikh.nano")));
        assertNull(Shadows.shadowOf(host).getNextStartedActivity().getPackage());
    }

    @Test public void missingStoreFallsBackToBrowserSelectorOnceWithSameListing() {
        List<Router.LaunchReport> reports = new ArrayList<>();
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay()
                .launchDiagnostics(reports::add).build();
        NoStoreActivity missing = Robolectric.buildActivity(NoStoreActivity.class).setup().get();
        Intent tap = click("mixed-missing-store", "url", "com.chikh.nano");
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(missing, tap));
        assertEquals(1, missing.nativeAttempts);
        Intent fallback = Shadows.shadowOf(missing).getNextStartedActivity();
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano", fallback.getDataString());
        assertNull(fallback.getPackage());
        assertTrue(fallback.getSelector().hasCategory(Intent.CATEGORY_APP_BROWSER));
        assertEquals("NOT_FOUND", reports.get(1).outcome);
        assertEquals("STORE_FALLBACK", reports.get(2).outcome);
        assertEquals(reports.get(0).destinationFingerprint, reports.get(3).destinationFingerprint);
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(missing, tap));
        assertEquals(1, missing.nativeAttempts);
        assertNull(Shadows.shadowOf(missing).getNextStartedActivity());
    }

    @Test @Config(shadows = InterceptingPackageManager.class)
    public void packageConstrainedStoreMustNotLaunchKnownDifferentMarket() {
        List<Router.LaunchReport> reports = new ArrayList<>();
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay()
                .launchDiagnostics(reports::add).build();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host,
                click("intermediary", "url", "com.chikh.nano")));
        assertEquals("STORE_HANDLER_MISMATCH", reports.get(0).outcome);
        assertEquals("STORE_FALLBACK", reports.get(1).outcome);
        Intent fallback = Shadows.shadowOf(host).getNextStartedActivity();
        assertNull(fallback.getPackage());
        assertTrue(fallback.getSelector().hasCategory(Intent.CATEGORY_APP_BROWSER));
        assertNull(Shadows.shadowOf(host).getNextStartedActivity());
    }

    @Test @Config(shadows = InterceptingPackageManager.class)
    public void mismatchedResolverUsesOnlyAnExportedOfficialStoreHandler() {
        Intent storeQuery = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.chikh.nano"))
                .setPackage("com.android.vending");
        ResolveInfo listing = new ResolveInfo();
        listing.activityInfo = new ActivityInfo();
        listing.activityInfo.packageName = "com.android.vending";
        listing.activityInfo.name = "com.android.vending.TestListingActivity";
        listing.activityInfo.exported = true; listing.activityInfo.enabled = true;
        listing.activityInfo.applicationInfo = new android.content.pm.ApplicationInfo();
        listing.activityInfo.applicationInfo.enabled = true;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(storeQuery, listing);
        List<Router.LaunchReport> reports = new ArrayList<>();
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay()
                .launchDiagnostics(reports::add).build();
        Intent click = click("verified-store-handler", "url", "com.chikh.nano");
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click));
        Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals(new ComponentName("com.android.vending", listing.activityInfo.name), opened.getComponent());
        assertEquals("com.android.vending", opened.getPackage()); assertNull(opened.getSelector());
        assertEquals(storeQuery.getData(), opened.getData());
        assertEquals("VERIFIED_STORE_HANDLER", reports.get(1).outcome);
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(host, click));
        assertNull(Shadows.shadowOf(host).getNextStartedActivity());
    }

    @Test @Config(shadows = InterceptingPackageManager.class)
    public void mismatchedResolverNeverUsesPrivateStoreActivity() {
        Intent query = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.chikh.nano"))
                .setPackage("com.android.vending");
        ResolveInfo listing = new ResolveInfo(); listing.activityInfo = new ActivityInfo();
        listing.activityInfo.packageName = "com.android.vending"; listing.activityInfo.name = "com.android.vending.PrivateActivity";
        listing.activityInfo.enabled = true; listing.activityInfo.exported = false;
        listing.activityInfo.applicationInfo = new android.content.pm.ApplicationInfo(); listing.activityInfo.applicationInfo.enabled = true;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(query, listing);
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay().build();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click("private-store-handler", "url", "com.chikh.nano")));
        Intent fallback = Shadows.shadowOf(host).getNextStartedActivity();
        assertNull(fallback.getComponent()); assertNull(fallback.getPackage());
        assertTrue(fallback.getSelector().hasCategory(Intent.CATEGORY_APP_BROWSER));
    }

    @Test public void missingStoreAndBrowserRemainContainedWithoutSecondClickRetry() {
        OpenPush configured = builder().httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay().build();
        Activity unavailable = Robolectric.buildActivity(NoBrowserActivity.class).setup().get();
        Intent tap = click("mixed-no-handlers", "url", "com.chikh.nano");
        assertEquals(OpenPush.ClickResult.HANDLER_FAILED, configured.handleIntent(unavailable, tap));
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(unavailable, tap));
    }

    @Test public void barePackageNormalizesAndNativeLaunchIsClaimedOnlyOnce() {
        OpenPush configured = builder().httpsHost("play.google.com").preferGooglePlay().build();
        Intent tap = click("package", "url", "com.chikh.nano");
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, tap));
        Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano", opened.getDataString());
        assertEquals("com.android.vending", opened.getPackage());
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(host, tap));
        assertNull(Shadows.shadowOf(host).getNextStartedActivity());
    }

    @Test public void httpApprovalIsIndependentFromHttpsAndPreservesWebDestination() {
        Intent tap = click("http", "url", "http://example.com/article/42?private=value");
        assertEquals(OpenPush.ClickResult.REJECTED, push.handleIntent(host, tap));
        assertEquals(OpenPush.ClickResult.OPENED, builder().httpHost("example.com").build().handleIntent(host, tap));
        Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals(tap.getStringExtra("op_target"), opened.getDataString());
        assertNull(opened.getPackage());
        assertFalse(opened.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertNull(opened.getSelector());
    }

    @Test public void malformedStoreIdsAndRandomStringsNeverLaunchOrClaim() {
        OpenPush configured = builder().httpsHost("play.google.com").preferGooglePlay().build();
        for (String value : new String[]{"random text", "nano", "com..nano", "com.123.nano",
                "https://play.google.com/store/apps/details?id=com.foo&id=com.bar",
                "https://play.google.com/store/apps/details?id=com.foo%2Fbar"}) {
            assertEquals(OpenPush.ClickResult.REJECTED, configured.handleIntent(host, click("same", "url", value)));
            assertNull(Shadows.shadowOf(host).getNextStartedActivity());
        }
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click("same", "url", "com.chikh.nano")));
    }

    @Test public void deniedNativeLaunchFallsBackAndReportsOnlySafeMetadata() {
        List<Router.LaunchReport> reports = new ArrayList<>();
        OpenPush configured = builder().httpsHost("play.google.com").preferGooglePlay().launchDiagnostics(reports::add).build();
        Activity denied = Robolectric.buildActivity(DeniedStoreActivity.class).setup().get();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(denied, click("denied", "url", "com.chikh.nano")));
        assertEquals(5, reports.size());
        assertEquals("DENIED", reports.get(1).outcome);
        assertEquals("com.android.vending", reports.get(0).requestedPackage);
        assertEquals(Intent.ACTION_VIEW, reports.get(0).action);
        assertEquals("https", reports.get(0).scheme);
        assertEquals("STORE_FALLBACK", reports.get(2).outcome);
        assertNull(reports.get(3).requestedPackage);
        assertEquals("RETURNED", reports.get(4).outcome);
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano", Shadows.shadowOf(denied).getNextStartedActivity().getDataString());
        OpenPush brokenLogger = builder().httpsHost("play.google.com").preferGooglePlay()
                .launchDiagnostics(report -> { throw new IllegalStateException("diagnostics failure"); }).build();
        assertEquals(OpenPush.ClickResult.OPENED, brokenLogger.handleIntent(host, click("logger", "url", "com.chikh.nano")));
    }

    @Test public void missingNativeStoreFallsBackToSameApprovedHttpsOnceAndContainsFailure() {
        OpenPush configured = builder().httpsHost("play.google.com").preferGooglePlay().build();
        NoStoreActivity noStore = Robolectric.buildActivity(NoStoreActivity.class).setup().get();
        Intent play = click("play", "url", "https://play.google.com/store/apps/details?id=com.chikh.nano");
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(noStore, play));
        assertEquals(1, noStore.nativeAttempts);
        Intent fallback = Shadows.shadowOf(noStore).getNextStartedActivity();
        assertNull(fallback.getPackage());
        assertEquals(play.getStringExtra("op_target"), fallback.getDataString());
        assertEquals(OpenPush.ClickResult.DUPLICATE, configured.handleIntent(noStore, play));
        assertEquals(1, noStore.nativeAttempts);
        Activity neither = Robolectric.buildActivity(NoBrowserActivity.class).setup().get();
        assertEquals(OpenPush.ClickResult.HANDLER_FAILED, configured.handleIntent(neither,
                new Intent(play).putExtra("op_id", "neither")));
    }

    @Test public void diagnosticCallbackCannotBreakRouting() {
        OpenPush configured = builder().diagnostics(event -> { throw new IllegalStateException("test"); }).build();
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click("a", "screen", "offers")));
        assertEquals(1, opens);
    }

    @Test public void missingBrowserIsContainedAndNotRetried() {
        Activity noBrowser = Robolectric.buildActivity(NoBrowserActivity.class).setup().get();
        Intent intent = click("browser", "url", "https://example.com/read/42");
        assertEquals(OpenPush.ClickResult.HANDLER_FAILED, push.handleIntent(noBrowser, intent));
        assertEquals(OpenPush.ClickResult.DUPLICATE, push.handleIntent(noBrowser, intent));
        assertTrue(events.contains(OpenPush.Event.HANDLER_FAILED));
    }

    @Test public void deepLinkMustHaveAnAllowedDeclaredDestination() {
        OpenPush configured = builder().deepLink("openpush", "article", "/read", ArticleActivity.class).build();
        Intent click = click("a", "deeplink", "openpush://article/read/42");
        assertEquals(OpenPush.ClickResult.REJECTED, configured.handleIntent(host, click));
        Intent candidate = new Intent(Intent.ACTION_VIEW, Uri.parse("openpush://article/read/42"))
                .setPackage(context.getPackageName()).addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = context.getPackageName();
        info.activityInfo.name = ArticleActivity.class.getName();
        info.isDefault = true;
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(candidate, info);
        assertEquals(OpenPush.ClickResult.OPENED, configured.handleIntent(host, click));
        Intent opened = Shadows.shadowOf(host).getNextStartedActivity();
        assertEquals(new ComponentName(context.getPackageName(), ArticleActivity.class.getName()), opened.getComponent());
        assertEquals("42", opened.getStringExtra(Router.ARGUMENT_PREFIX + "article_id"));
    }

    @Test public void deepLinkRejectsUnregisteredPathsAndTraversal() {
        OpenPush configured = builder().deepLink("openpush", "article", "/read", ArticleActivity.class).build();
        for (String target : new String[]{"openpush://article/reader/42", "openpush://other/read/42",
                "openpush://article/read/../admin", "openpush://article/read/%2e%2e/admin",
                "openpush://article/read%2fadmin", "openpush://article/read/%252e%252e/admin", "intent://article/read/42"}) {
            assertEquals(OpenPush.ClickResult.REJECTED, configured.handleIntent(host, click("a", "deeplink", target)));
        }
    }

    @Test public void localNotificationDoesNotNavigateUntilTap() {
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_id", "local"); data.put("op_type", "screen"); data.put("op_target", "offers");
        data.put("op_title", "Title"); data.put("op_body", "Body");
        assertTrue(push.showNotification(new RemoteMessage.Builder("test").setData(data).build()));
        assertEquals(0, opens);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals(1, manager.getActiveNotifications().length);
        assertEquals("Title", manager.getActiveNotifications()[0].getNotification().extras.getString("android.title"));
    }

    @Test public void channelNameIsNotOverwrittenByDelivery() {
        push.createChannel("Host's channel");
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_id", "local"); data.put("op_type", "screen"); data.put("op_target", "offers");
        push.showNotification(new RemoteMessage.Builder("test").setData(data).build());
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals("Host's channel", manager.getNotificationChannel(OpenPush.CHANNEL_ID).getName());
    }

    @Test public void differentNotificationsKeepIndependentClickPayloads() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_type", "screen"); data.put("op_target", "offers");
        data.put("op_id", "first");
        push.showNotification(new RemoteMessage.Builder("test").setData(data).build());
        data.put("op_id", "second");
        push.showNotification(new RemoteMessage.Builder("test").setData(data).build());
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals(2, manager.getActiveNotifications().length);
        android.app.PendingIntent first = null, second = null;
        for (android.service.notification.StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getTag().endsWith("first")) first = notification.getNotification().contentIntent;
            else second = notification.getNotification().contentIntent;
        }
        assertNotNull(first); assertNotNull(second); assertNotEquals(first, second);
        first.send();
        Intent opened = Shadows.shadowOf((android.app.Application) context).getNextStartedActivity();
        assertEquals("first", opened.getStringExtra("op_id"));
        assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(host, opened));
        assertEquals(OpenPush.ClickResult.DUPLICATE, push.handleIntent(host, opened));
        assertEquals(1, opens);
    }

    @Test @Config(sdk = 33) public void notificationPermissionDenialAndGrantAreRespected() {
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_id", "permission"); data.put("op_type", "screen"); data.put("op_target", "offers");
        Shadows.shadowOf((android.app.Application) context).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        assertTrue(push.showNotification(new RemoteMessage.Builder("test").setData(data).build()));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals(0, manager.getActiveNotifications().length);
        assertTrue(events.contains(OpenPush.Event.PERMISSION_DENIED));
        Shadows.shadowOf((android.app.Application) context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        assertTrue(push.showNotification(new RemoteMessage.Builder("test").setData(data).build()));
        assertEquals(1, manager.getActiveNotifications().length);
    }

    @Test public void pipelineProvesPackagePreservedUntilNormalizationAndOutgoingIntent() throws Exception {
        List<PipelineReport> reports = new ArrayList<>();
        List<Router.LaunchReport> launches = new ArrayList<>();
        OpenPush traced = OpenPush.builder(context, Activity.class, android.R.drawable.ic_dialog_info)
                .httpsHost("play.google.com").preferGooglePlay()
                .pipelineDiagnostics(reports::add).launchDiagnostics(launches::add).build();
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_id", "traced-package"); data.put("op_type", "url");
        data.put("op_target", "com.chikh.nano"); data.put("private_value", "never-log-this");
        assertTrue(traced.showNotification(new RemoteMessage.Builder("test").setData(data).build()));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.getActiveNotifications()[0].getNotification().contentIntent.send();
        Intent click = Shadows.shadowOf((android.app.Application) context).getNextStartedActivity();
        assertEquals("com.chikh.nano", click.getStringExtra("op_target"));
        assertEquals(OpenPush.ClickResult.OPENED, traced.handleIntent(host, click));
        PipelineReport received = reports.get(0);
        PipelineReport clicked = reports.stream().filter(r -> "CLICK".equals(r.stage)).findFirst().get();
        assertEquals("PACKAGE_TO_PLAY", received.kind);
        assertTrue(received.keys.contains("private_value"));
        assertEquals(received.inputFingerprint, clicked.inputFingerprint);
        assertEquals(received.targetFingerprint, clicked.targetFingerprint);
        assertNotEquals(received.inputFingerprint, received.targetFingerprint);
        assertEquals(received.targetFingerprint, launches.get(0).destinationFingerprint);
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano",
                Shadows.shadowOf(host).getNextStartedActivity().getDataString());
        assertEquals("com.android.vending", launches.get(0).requestedPackage);
        assertEquals(OpenPush.ClickResult.DUPLICATE, traced.handleIntent(host, click));
        assertEquals(2, launches.size()); // REQUESTED and RETURNED, exactly one attempt.
    }

    @Test public void pipelineExplainsIgnoredForeignKeysAndCannotBreakRouting() {
        List<PipelineReport> reports = new ArrayList<>();
        OpenPush traced = builder().pipelineDiagnostics(reports::add).build();
        assertEquals(OpenPush.ClickResult.IGNORED,
                traced.handleIntent(host, new Intent().putExtra("url", "https://example.com/private?secret=x")));
        assertTrue(reports.get(0).keys.contains("url"));
        assertEquals("IGNORED_NO_PROTOCOL", reports.get(0).decision);
        assertEquals("NONE", reports.get(0).inputFingerprint);
        OpenPush brokenListener = builder().pipelineDiagnostics(report -> { throw new IllegalStateException(); }).build();
        assertEquals(OpenPush.ClickResult.OPENED, brokenListener.handleIntent(host, click("listener", "screen", "offers")));
    }

    @Test public void disabledChannelDoesNotDisplayOrNavigate() {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new android.app.NotificationChannel(OpenPush.CHANNEL_ID,
                "Disabled", NotificationManager.IMPORTANCE_NONE));
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_id", "disabled"); data.put("op_type", "screen"); data.put("op_target", "offers");
        assertTrue(push.showNotification(new RemoteMessage.Builder("test").setData(data).build()));
        assertEquals(0, manager.getActiveNotifications().length);
        assertEquals(0, opens);
        assertTrue(events.contains(OpenPush.Event.CHANNEL_DISABLED));
    }
}
