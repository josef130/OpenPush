package io.openpush.sample;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.os.Build;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.firebase.messaging.RemoteMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses the real posted notification's PendingIntent. Does not claim FCM delivery or visual Store success. */
@RunWith(AndroidJUnit4.class)
public final class PendingIntentDeviceTest {
    @Test public void dispatchPostedNotification() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SampleApplication app = (SampleApplication) context.getApplicationContext();
        String kind = InstrumentationRegistry.getArguments().getString("routeCase", "web");
        AtomicInteger main = new AtomicInteger(), article = new AtomicInteger();
        Application.ActivityLifecycleCallbacks observer = new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { }
            public void onActivityStarted(Activity a) { }
            public void onActivityResumed(Activity a) {
                if (a instanceof MainActivity) main.incrementAndGet();
                if (a instanceof ArticleActivity) article.incrementAndGet();
            }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            public void onActivityDestroyed(Activity a) { }
        };
        app.registerActivityLifecycleCallbacks(observer);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = "pending-qa-" + kind + "-" + System.currentTimeMillis();
        try {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            device.wakeUp();
            assertFalse("Unlock the phone before visible routing tests", ((KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());
            device.pressHome();
            Map<String, String> data = new HashMap<>();
            data.put("op_v", "1"); data.put("op_id", id);
            data.put("op_type", "deep".equals(kind) ? "deeplink" : "app".equals(kind) ? "app" : "url");
            data.put("op_target", "play".equals(kind) ? "https://play.google.com/store/apps/details?id=com.chikh.nano"
                    : "package".equals(kind) ? "com.chikh.nano" : "deep".equals(kind) ? "openpush://article/read/42"
                    : "app".equals(kind) ? "main" : "https://www.google.com");
            data.put("op_title", "OpenPush pending QA " + kind);
            data.put("op_body", "Local route test; not FCM delivery");
            assertTrue("Notification must be posted", app.push.showNotification(new RemoteMessage.Builder("qa").setData(data).build()));
            PendingIntent click = null;
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                if (("openpush:" + id).equals(notification.getTag())) click = notification.getNotification().contentIntent;
            }
            assertNotNull("Use actual posted notification PendingIntent", click);
            int openedBefore = app.journal.count(TestJournal.Event.OPENED);
            if (Build.VERSION.SDK_INT >= 34) {
                Bundle options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
                click.send(context, 0, null, null, null, null, options);
            } else click.send();
            SystemClock.sleep(2000);
            int openedAfterFirst = app.journal.count(TestJournal.Event.OPENED);
            if (Build.VERSION.SDK_INT >= 34) {
                Bundle options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
                click.send(context, 0, null, null, null, null, options);
            } else click.send();
            SystemClock.sleep(500);
            JSONObject result = new JSONObject().put("kind", kind).put("source", "local posted notification PendingIntent; not FCM")
                    .put("mainResumes", main.get()).put("articleResumes", article.get())
                    .put("foregroundPackage", device.getCurrentPackageName())
                    .put("openedDelta", openedAfterFirst - openedBefore)
                    .put("duplicateExecutionPrevented", app.journal.count(TestJournal.Event.OPENED) == openedAfterFirst)
                    .put("nativeStorePageVisuallyConfirmed", false);
            try (FileOutputStream output = new FileOutputStream(new File(context.getCacheDir(), "pending-route-" + kind + ".json"))) {
                output.write(result.toString().getBytes(StandardCharsets.UTF_8));
            }
            assertEquals("Execute once", 1, openedAfterFirst - openedBefore);
            assertEquals("Repeat PendingIntent must not execute again", openedAfterFirst, app.journal.count(TestJournal.Event.OPENED));
            if ("app".equals(kind)) assertTrue("App destination resumes host", main.get() > 0);
            else assertEquals("Destination must not resume launcher", 0, main.get());
            if ("deep".equals(kind)) assertTrue("Deep link resumes article screen", article.get() > 0);
        } finally {
            manager.cancel("openpush:" + id, 0);
            app.unregisterActivityLifecycleCallbacks(observer);
        }
    }
}
