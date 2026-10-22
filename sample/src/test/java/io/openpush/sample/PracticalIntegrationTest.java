package io.openpush.sample;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import android.widget.TextView;
import io.openpush.sdk.Router;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real sample classes/merged manifest under simulated Android. NOT a Firebase transport test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = SampleApplication.class)
public class PracticalIntegrationTest {
    private SampleApplication app;
    private NotificationManager notifications;
    private MessagingService service;

    @Before public void setup() {
        app = (SampleApplication) RuntimeEnvironment.getApplication();
        app.getSharedPreferences("openpush.clicks.v1", 0).edit().clear().commit();
        app.getSharedPreferences("openpush.sample.qa", 0).edit().clear().commit();
        notifications = (NotificationManager) app.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        notifications.cancelAll();
        service = Robolectric.buildService(MessagingService.class).create().get();
    }
    private PendingIntent tapFor(String tagSuffix) {
        for (StatusBarNotification notification : notifications.getActiveNotifications()) {
            if (notification.getTag().endsWith(tagSuffix)) return notification.getNotification().contentIntent;
        }
        throw new AssertionError("No notification with suffix " + tagSuffix);
    }
    private Intent deliverTap(String suffix) throws Exception {
        tapFor(suffix).send();
        Intent intent = Shadows.shadowOf(app).getNextStartedActivity();
        assertNotNull(intent);
        String type=intent.getStringExtra("op_type");
        Class<?> expected=("url".equals(type)||"deeplink".equals(type))?io.openpush.sdk.OpenPushClickActivity.class:MainActivity.class;
        assertEquals(expected.getName(), intent.getComponent().getClassName());
        return intent;
    }
    private String result(MainActivity activity) {
        return ((TextView) activity.findViewById(android.R.id.content).findViewWithTag("openpush.result")).getText().toString();
    }

    @Test public void ordinaryNotificationUsesExistingHostHandlerAndOpensHome() throws Exception {
        service.onMessageReceived(LocalFixtures.message("plain", null, null, true));
        assertEquals(1, notifications.getActiveNotifications().length);
        assertEquals(1, app.journal.count(TestJournal.Event.HOST_DISPLAYED));
        assertEquals(0, app.journal.count(TestJournal.Event.SDK_OWNED));
        MainActivity activity = Robolectric.buildActivity(MainActivity.class, deliverTap("plain")).setup().get();
        assertTrue(result(activity).contains("IGNORED"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void googleAndPlayCustomDataKeepWebBrowserAndNativeStoreIndependent() throws Exception {
        String[] targets = {"https://www.google.com", "https://play.google.com/store/apps/details?id=com.chikh.nano"};
        for (int i = 0; i < targets.length; i++) {
            service.onMessageReceived(LocalFixtures.message("approved-web-" + i, "url", targets[i], true));
        }
        assertEquals(2, notifications.getActiveNotifications().length);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class,
                deliverTap("approved-web-0")).setup();
        MainActivity activity = controller.get();
        Intent google = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(google);
        assertEquals(Intent.ACTION_VIEW, google.getAction());
        assertEquals(targets[0], google.getDataString());
        assertNull(google.getComponent());
        assertNull(google.getPackage());
        assertNull(google.getSelector()); // Ordinary web uses Android default resolution, no forced browser selector.
        controller.newIntent(deliverTap("approved-web-1"));
        Intent console = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(console);
        assertEquals(Intent.ACTION_VIEW, console.getAction());
        assertEquals(targets[1], console.getDataString());
        assertNull(console.getComponent());
        assertEquals("com.android.vending", console.getPackage());
        assertNull(console.getSelector());
        assertEquals(2, app.journal.count(TestJournal.Event.OPENED));
    }

    @Test public void incompleteRoutingDataNeverLaunchesAndCanBeCorrectedWithSameUnclaimedId() {
        Intent incomplete = new Intent().putExtra("op_id", "missing-protocol")
                .putExtra("op_target", "https://www.google.com");
        MainActivity activity = Robolectric.buildActivity(MainActivity.class, incomplete).setup().get();
        assertTrue(result(activity).contains("IGNORED"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertEquals(io.openpush.sdk.OpenPush.ClickResult.REJECTED,
                app.push.handleIntent(activity, new Intent(incomplete).putExtra("op_v", "1")));
        assertEquals(io.openpush.sdk.OpenPush.ClickResult.OPENED,
                app.push.handleIntent(activity, new Intent(incomplete).putExtra("op_v", "1").putExtra("op_type", "url")));
        assertEquals("https://www.google.com", Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
    }

    @Test public void notificationPlusDataOpensHttpsOnColdStartOnlyOnce() throws Exception {
        service.onMessageReceived(LocalFixtures.message("cold", "url", "https://example.com/?openpush=qa-A", true));
        assertEquals(1, app.journal.count(TestJournal.Event.SDK_OWNED));
        assertEquals(0, app.journal.count(TestJournal.Event.OPENED));
        Intent tap = deliverTap("cold");
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, tap).setup();
        MainActivity activity = controller.get();
        Intent browser = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Uri.parse("https://example.com/?openpush=qa-A"), browser.getData());
        assertEquals(Intent.ACTION_VIEW, browser.getAction());
        assertEquals(1, app.journal.count(TestJournal.Event.OPENED));
        controller.newIntent(new Intent(tap));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertTrue(result(activity).contains("DUPLICATE"));
        controller.recreate();
        assertEquals(1, app.journal.count(TestJournal.Event.OPENED));
        assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
    }

    @Test public void multipleOutstandingNotificationsKeepChangedUrlsAndWarmEntryCorrect() throws Exception {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        service.onMessageReceived(LocalFixtures.message("A", "url", "https://example.com/?openpush=qa-A", true));
        service.onMessageReceived(LocalFixtures.message("B", "url", "https://example.com/?openpush=qa-B", true));
        service.onMessageReceived(LocalFixtures.message("C", null, null, true));
        assertEquals(3, notifications.getActiveNotifications().length);
        controller.newIntent(deliverTap("B"));
        assertEquals("qa-B", Shadows.shadowOf(activity).getNextStartedActivity().getData().getQueryParameter("openpush"));
        controller.newIntent(deliverTap("A"));
        assertEquals("qa-A", Shadows.shadowOf(activity).getNextStartedActivity().getData().getQueryParameter("openpush"));
        assertEquals(2, app.journal.count(TestJournal.Event.OPENED));
        assertEquals(3, notifications.getActiveNotifications().length);
        // Robolectric PendingIntent.send does not model the system tray's auto-cancel gesture.
    }

    @Test public void declaredDeepLinkOpensActualArticleViewWithContentAndLevel() throws Exception {
        service.onMessageReceived(LocalFixtures.message("article", "deeplink", "openpush://article/read/42", true));
        MainActivity main = Robolectric.buildActivity(MainActivity.class, deliverTap("article")).setup().get();
        Intent destination = Shadows.shadowOf(main).getNextStartedActivity();
        assertNotNull(destination);
        assertEquals(ArticleActivity.class.getName(), destination.getComponent().getClassName());
        assertEquals("42", destination.getStringExtra(Router.ARGUMENT_PREFIX + "article_id"));
        assertEquals("3", destination.getStringExtra(Router.ARGUMENT_PREFIX + "level"));
        assertFalse(destination.hasExtra("op_v"));
        ArticleActivity article = Robolectric.buildActivity(ArticleActivity.class, destination).setup().get();
        String text = ((TextView) article.findViewById(android.R.id.content).findViewWithTag("openpush.article")).getText().toString();
        assertTrue(text.contains("Article 42"));
        assertTrue(text.contains("Level: 3"));
    }

    @Test public void dataOnlyAndCombinedMessagesRemainDistinctFixtures() {
        com.google.firebase.messaging.RemoteMessage data = LocalFixtures.message("data", "screen", "offers", false);
        com.google.firebase.messaging.RemoteMessage combined = LocalFixtures.message("combo", "screen", "offers", true);
        assertNull(data.getNotification());
        assertNotNull(combined.getNotification());
        assertEquals("offers", data.getData().get("op_target"));
        assertEquals("offers", combined.getData().get("op_target"));
        service.onMessageReceived(data);
        service.onMessageReceived(combined);
        assertEquals(2, notifications.getActiveNotifications().length);
        assertEquals(0, app.journal.count(TestJournal.Event.OPENED));
        // Direct callbacks exercise foreground service processing, not FCM background dispatch.
    }

    @Test public void playHttpsAndBarePackageOpenTheSameNativeStoreDestination() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        controller.newIntent(new Intent().putExtra("op_v", "1").putExtra("op_id", "play-url")
                .putExtra("op_type", "url").putExtra("op_target", "https://play.google.com/store/apps/details?id=com.chikh.nano"));
        Intent store = Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertNotNull(store);
        assertEquals(Intent.ACTION_VIEW, store.getAction());
        assertEquals("com.android.vending", store.getPackage());
        assertNull(store.getSelector());
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano", store.getDataString());
        controller.newIntent(new Intent().putExtra("op_v", "1").putExtra("op_id", "bare-package")
                .putExtra("op_type", "url").putExtra("op_target", "com.chikh.nano"));
        assertTrue(result(controller.get()).contains("OPENED"));
        Intent fromPackage = Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertEquals(store.getData(), fromPackage.getData());
        assertEquals("com.android.vending", fromPackage.getPackage());
        assertNull(fromPackage.getSelector());
    }

    @Test public void invalidLinksAndUnsupportedDestinationsFailWithoutNavigation() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        for (String[] spec : new String[][]{{"url", "javascript:alert(1)"}, {"url", "https://evil.example/"},
                {"deeplink", "openpush://missing/read/42"}, {"missing_action", "unknown"}}) {
            controller.newIntent(new Intent().putExtra("op_v", "1").putExtra("op_id", "reject")
                    .putExtra("op_type", spec[0]).putExtra("op_target", spec[1]));
            assertTrue(result(controller.get()).contains("REJECTED"));
            assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
        }
        assertEquals(0, app.journal.count(TestJournal.Event.OPENED));
    }

    @Test public void existingServiceCallbacksAndUnrelatedDataArePreserved() {
        service.onNewToken("fake-test-token-never-recorded");
        service.onDeletedMessages();
        service.onMessageReceived(new com.google.firebase.messaging.RemoteMessage.Builder("fixture")
                .addData("existing_business_field", "value").build());
        assertEquals(1, app.journal.count(TestJournal.Event.TOKEN_CALLBACK));
        assertEquals(1, app.journal.count(TestJournal.Event.DELETED_CALLBACK));
        assertEquals(1, app.journal.count(TestJournal.Event.HOST_DATA));
        assertEquals(0, app.journal.count(TestJournal.Event.SDK_OWNED));
        assertFalse(app.journal.summary().contains("fake-test-token"));
        List<ResolveInfo> services = app.getPackageManager().queryIntentServices(
                new Intent("com.google.firebase.MESSAGING_EVENT").setPackage(app.getPackageName()), 0);
        int hostServices = 0;
        for (ResolveInfo info : services) {
            if (info.serviceInfo.name.startsWith("io.openpush.sample.")) hostServices++;
        }
        assertEquals(1, hostServices);
    }

    @Test public void localSimulationNeverIncrementsFcmCallbackCounter() {
        app.journal.record(TestJournal.Event.LOCAL_SIMULATION);
        HostMessageHandler.receive(app, LocalFixtures.next("url", "https://example.com/?openpush=local"));
        assertEquals(0, app.journal.count(TestJournal.Event.FCM_CALLBACK));
        assertEquals(1, app.journal.count(TestJournal.Event.LOCAL_SIMULATION));
    }
}
