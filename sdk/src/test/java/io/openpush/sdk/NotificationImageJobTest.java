package io.openpush.sdk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.service.notification.StatusBarNotification;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class NotificationImageJobTest {
    @Test public void upgradePreservesClickAndDoesNotRecreateDismissedNotification() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("image-test", "Test", 3));
        PendingIntent click = PendingIntent.getActivity(context, 12, new Intent(context, android.app.Activity.class), PendingIntent.FLAG_IMMUTABLE);
        manager.notify("image-test", 0, new Notification.Builder(context, "image-test")
                .setSmallIcon(1).setContentTitle("Image").setContentIntent(click).setAutoCancel(true).build());
        StatusBarNotification posted = manager.getActiveNotifications()[0];
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        assertTrue(NotificationImageJob.upgrade(context, "image-test", posted.getPostTime(), bitmap));
        Notification updated = manager.getActiveNotifications()[0].getNotification();
        assertEquals(click, updated.contentIntent);
        assertEquals("android.app.Notification$BigPictureStyle", updated.extras.getString(Notification.EXTRA_TEMPLATE));
        manager.cancel("image-test", 0);
        assertFalse(NotificationImageJob.upgrade(context, "image-test", posted.getPostTime(), bitmap));
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test public void rejectsUnsafeImageUrls() {
        assertTrue(NotificationImageJob.safe("https://example.com/image.jpg"));
        for (String value : new String[]{null, "http://example.com/a", "file:///a", "https://user:secret@example.com/a", "https://example.com:8443/a", "not a url"})
            assertFalse(NotificationImageJob.safe(value));
    }
}
