package io.openpush.sample;

import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import io.openpush.sdk.OpenPush;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Physical-device probes. Tokens stay in private cache and never enter test output/evidence. */
@RunWith(AndroidJUnit4.class)
public final class DeviceAcceptanceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private void write(String filename, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(context.getCacheDir(), filename))) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
    private NotificationManager notifications() { return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE); }
    private void foregroundSampleForDispatch() {
        UiDevice device = UiDevice.getInstance(instrumentation);
        for (int i = 0; i < 2 && "com.android.systemui".equals(device.getCurrentPackageName()); i++) {
            device.pressBack();
            SystemClock.sleep(200);
        }
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue("Sample must be visibly foreground before synthetic PendingIntent dispatch",
                device.wait(Until.hasObject(By.pkg("io.openpush.sample").depth(0)), 5000));
    }
    private JSONObject snapshot() throws Exception {
        JSONObject result = new JSONObject();
        result.put("api", Build.VERSION.SDK_INT).put("model", Build.MODEL)
                .put("notificationsEnabled", notifications().areNotificationsEnabled());
        NotificationChannel channel = notifications().getNotificationChannel(OpenPush.CHANNEL_ID);
        result.put("openpushChannelImportance", channel == null ? -1 : channel.getImportance());
        JSONArray active = new JSONArray();
        for (StatusBarNotification item : notifications().getActiveNotifications()) {
            active.put(new JSONObject().put("title", item.getNotification().extras.getString("android.title", ""))
                    .put("tag", item.getTag()).put("hasTap", item.getNotification().contentIntent != null)
                    .put("hasPicture", item.getNotification().extras.get("android.picture") != null
                            || item.getNotification().extras.get("android.pictureIcon") != null));
        }
        result.put("ownNotifications", active);
        TestJournal journal = new TestJournal(context);
        for (TestJournal.Event event : TestJournal.Event.values()) result.put(event.name(), journal.count(event));
        return result;
    }

    @Test public void captureTokenPrivately() throws Exception {
        assertFalse("A real Firebase configured APK is required", BuildConfig.LOCAL_ONLY_BUILD);
        String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 120, TimeUnit.SECONDS);
        assertNotNull(token);
        assertTrue("Token must not be empty", token.length() > 20);
        write("qa-fcm-token.txt", token);
        write("qa-device-probe.json", snapshot().put("tokenReady", true).toString(2));
    }

    @Test public void recordOwnNotifications() throws Exception {
        write("qa-device-probe.json", snapshot().toString(2));
    }

    @Test public void dispatchPlayStoreNotification() throws Exception {
        String title = InstrumentationRegistry.getArguments().getString("qaTitle");
        assertNotNull(title);
        android.app.PendingIntent pending = null;
        JSONObject evidence = snapshot().put("notificationTitle", title)
                .put("dispatchMethod", "PENDING_INTENT_SEND_NOT_SYSTEM_TRAY_TAP");
        write("qa-store-evidence.json", evidence.put("status", "BEFORE_DISPATCH").toString(2));
        for (StatusBarNotification item : notifications().getActiveNotifications()) {
            if (title.equals(item.getNotification().extras.getString("android.title"))) pending = item.getNotification().contentIntent;
        }
        assertNotNull("Named real Console notification must arrive first", pending);
        foregroundSampleForDispatch();
        TestJournal journal = new TestJournal(context);
        int opened = journal.count(TestJournal.Event.OPENED);
        pending.send();
        UiDevice device = UiDevice.getInstance(instrumentation);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!"com.android.vending".equals(device.getCurrentPackageName()) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        evidence.put("foregroundPackage", device.getCurrentPackageName()).put("after", snapshot())
                .put("status", "com.android.vending".equals(device.getCurrentPackageName()) ? "STORE_VISIBLE" : "STORE_NOT_VISIBLE");
        write("qa-store-evidence.json", evidence.toString(2));
        assertEquals(opened + 1, journal.count(TestJournal.Event.OPENED));
        assertEquals("Expected native Google Play", "com.android.vending", device.getCurrentPackageName());
    }

    @Test public void dispatchBarePackageToPlayStore() throws Exception {
        assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano",
                io.openpush.core.Destination.parse("com.chikh.nano").url);
        dispatchPlayStoreNotification();
    }


    @Test public void localDestinationClassificationContracts() {
        assertEquals("http://example.com/read", io.openpush.core.Destination.parse("http://example.com/read").url);
        assertEquals("https://example.com/read", io.openpush.core.Destination.parse("https://example.com/read").url);
        assertEquals(io.openpush.core.Destination.parse("com.chikh.nano").url,
                io.openpush.core.Destination.parse("https://play.google.com/store/apps/details?id=com.chikh.nano").url);
        assertNull(io.openpush.core.Destination.parse("random text"));
        assertNull(io.openpush.core.Destination.parse("https://play.google.com/store/apps/details?id=com.foo&id=com.bar"));
    }

    /** Real Android notification/host behavior with synthetic input; NOT FCM transport. */
    @Test public void localHostAndOutstandingNotifications() throws Exception {
        SampleApplication app = (SampleApplication) context.getApplicationContext();
        TestJournal journal = new TestJournal(context);
        long callbacks = journal.count(TestJournal.Event.FCM_CALLBACK);
        long displayed = journal.count(TestJournal.Event.HOST_DISPLAYED);
        long hostData = journal.count(TestJournal.Event.HOST_DATA);
        assertTrue("Enable the sample notification permission first", notifications().areNotificationsEnabled());
        String prefix = "device-local-" + java.util.UUID.randomUUID();
        try {
            HostMessageHandler.receive(app, LocalFixtures.message(prefix + "-plain", null, null, true));
            assertEquals(displayed + 1, journal.count(TestJournal.Event.HOST_DISPLAYED));
            HostMessageHandler.receive(app, new com.google.firebase.messaging.RemoteMessage.Builder("local")
                    .addData("host_event", "fixture").build());
            assertEquals(hostData + 1, journal.count(TestJournal.Event.HOST_DATA));
            assertTrue(app.push.showNotification(LocalFixtures.message(prefix + "-A", "url", "https://example.com/?openpush=local-A", true)));
            assertTrue(app.push.showNotification(LocalFixtures.message(prefix + "-B", "url", "https://example.com/?openpush=local-B", false)));
            boolean plain = false, a = false, b = false;
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (!(plain && a && b) && SystemClock.elapsedRealtime() < deadline) {
                for (StatusBarNotification item : notifications().getActiveNotifications()) {
                    if (("sample.host:" + prefix + "-plain").equals(item.getTag())) plain = true;
                    if (("openpush:" + prefix + "-A").equals(item.getTag())) a = true;
                    if (("openpush:" + prefix + "-B").equals(item.getTag())) b = true;
                }
                if (!(plain && a && b)) SystemClock.sleep(50);
            }
            assertTrue("Three separate real Android notifications must coexist", plain && a && b);
            assertEquals("Local fixtures never prove FCM arrival", callbacks, journal.count(TestJournal.Event.FCM_CALLBACK));
        } finally {
            notifications().cancel("sample.host:" + prefix + "-plain", 0);
            notifications().cancel("openpush:" + prefix + "-A", 0);
            notifications().cancel("openpush:" + prefix + "-B", 0);
        }
    }

    /** Actual Activity entry points and persistent counters, without claiming remote delivery. */
    @Test public void localRejectedIntentsAndReplay() {
        TestJournal journal = new TestJournal(context);
        long opened = journal.count(TestJournal.Event.OPENED);
        long duplicate = journal.count(TestJournal.Event.DUPLICATE);
        long rejected = journal.count(TestJournal.Event.REJECTED);
        String prefix = "device-intent-" + java.util.UUID.randomUUID();
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("op_v", "1").putExtra("op_id", prefix)
                .putExtra("op_type", "screen").putExtra("op_target", "offers");
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        assertEquals(opened + 1, journal.count(TestJournal.Event.OPENED));
        instrumentation.runOnMainSync(() -> activity.onNewIntent(intent));
        instrumentation.waitForIdleSync();
        assertEquals(opened + 1, journal.count(TestJournal.Event.OPENED));
        assertEquals(duplicate + 1, journal.count(TestJournal.Event.DUPLICATE));
        instrumentation.runOnMainSync(() -> activity.onNewIntent(new Intent(intent).putExtra("op_id", prefix + "-bad")
                .putExtra("op_type", "url").putExtra("op_target", "javascript:alert(1)")));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> activity.onNewIntent(new Intent(intent).putExtra("op_id", prefix + "-unknown")
                .putExtra("op_type", "unsupported").putExtra("op_target", "missing")));
        instrumentation.waitForIdleSync();
        assertEquals(rejected + 2, journal.count(TestJournal.Event.REJECTED));
    }

    @Test public void tapConsoleNotificationAndCheckDestination() throws Exception {
        checkPendingDestination(false);
    }

    @Test public void dispatchPendingNotificationAndCheckDestination() throws Exception {
        checkPendingDestination(true);
    }

    private void checkPendingDestination(boolean direct) throws Exception {
        String title = InstrumentationRegistry.getArguments().getString("qaTitle");
        String expected = InstrumentationRegistry.getArguments().getString("qaExpected");
        assertNotNull("Pass qaTitle", title); assertNotNull("Pass qaExpected", expected);
        UiDevice device = UiDevice.getInstance(instrumentation);
        boolean present = false;
        android.app.PendingIntent pending = null;
        for (StatusBarNotification item : notifications().getActiveNotifications()) {
            if (title.equals(item.getNotification().extras.getString("android.title"))) {
                present = true;
                pending = item.getNotification().contentIntent;
            }
        }
        JSONObject evidence = snapshot().put("notificationTitle", title).put("expected", expected)
                .put("dispatchMethod", direct ? "PENDING_INTENT_SEND_NOT_SYSTEM_TRAY_TAP" : "SYSTEM_TRAY_TAP");
        write("qa-tap-evidence.json", evidence.put("ownNotificationPresent", present).toString(2));
        assertTrue("Expected Console notification has not arrived", present);
        if (direct) {
            foregroundSampleForDispatch();
            assertNotNull(pending);
            pending.send();
        } else {
            device.openNotification();
            UiObject2 notification = device.wait(Until.findObject(By.text(title)), 10000);
            assertNotNull("Expected notification title not visible in system tray", notification);
            notification.click();
            SystemClock.sleep(500);
            if ("com.android.systemui".equals(device.getCurrentPackageName())) {
                UiObject2 expanded = device.findObject(By.text(title));
                if (expanded != null) expanded.click();
            }
        }
        SystemClock.sleep(1500);
        if (expected.startsWith("https://")) {
            String observed = "";
            for (int attempt = 0; attempt < 25; attempt++) {
                observed = findAddress(false);
                if (!observed.isEmpty()) {
                    findAddress(true); // Focus only the observed address control to expose the full URL.
                    SystemClock.sleep(400);
                    String expanded = findAddress(false);
                    if (!expanded.isEmpty()) observed = expanded;
                    if (matches(expected, observed)) break;
                }
                SystemClock.sleep(300);
            }
            evidence.put("browserPackage", device.getCurrentPackageName()).put("observedAddress", observed)
                    .put("matched", matches(expected, observed));
            write("qa-tap-evidence.json", evidence.toString(2));
            assertTrue("Browser address did not match the expected HTTPS destination; see private QA evidence", matches(expected, observed));
            device.pressBack();
        } else {
            UiObject2 article = device.wait(Until.findObject(By.textContains(expected)), 10000);
            evidence.put("matched", article != null).put("foregroundPackage", device.getCurrentPackageName());
            if (article != null) evidence.put("observedArticle", article.getText());
            write("qa-tap-evidence.json", evidence.toString(2));
            assertNotNull("Expected internal destination was not visible", article);
        }
    }

    private boolean matches(String expected, String observed) {
        String address = observed.trim();
        if (!address.startsWith("https://")) address = "https://" + address;
        return expected.equals(address);
    }

    /** Inspect only address-like controls containing our controlled QA domain, never other text. */
    private String findAddress(boolean focus) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return "";
        Queue<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        while (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.remove();
            String id = node.getViewIdResourceName() == null ? "" : node.getViewIdResourceName().toLowerCase();
            String text = node.getText() == null ? "" : node.getText().toString();
            boolean addressControl = id.contains("url") || id.contains("address") || id.contains("omnibox")
                    || "android.widget.EditText".contentEquals(node.getClassName() == null ? "" : node.getClassName());
            if (addressControl && text.contains("example.com")) {
                if (focus) node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return text;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) nodes.add(child);
            }
        }
        return "";
    }

    @Test public void removePrivateTokenProbe() {
        File token = new File(context.getCacheDir(), "qa-fcm-token.txt");
        assertTrue(!token.exists() || token.delete());
    }
}
