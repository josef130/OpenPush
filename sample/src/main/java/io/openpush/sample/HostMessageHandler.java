package io.openpush.sample;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.google.firebase.messaging.RemoteMessage;
import java.util.UUID;

/** Representative pre-existing host logic. OpenPush delegates unrelated messages back here. */
public final class HostMessageHandler {
    public static final String CHANNEL_ID = "sample_host";
    private HostMessageHandler() { }

    public static void receive(SampleApplication app, RemoteMessage message) {
        if (app.push.showNotification(message)) {
            app.journal.record(TestJournal.Event.SDK_OWNED);
            return;
        }
        RemoteMessage.Notification source = message.getNotification();
        if (source == null) {
            app.journal.record(TestJournal.Event.HOST_DATA);
            return;
        }
        NotificationManager manager = (NotificationManager) app.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if ((Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) || !manager.areNotificationsEnabled()) {
            app.journal.record(TestJournal.Event.HOST_SUPPRESSED);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Host notifications",
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
            if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                app.journal.record(TestJournal.Event.HOST_SUPPRESSED);
                return;
            }
        }
        String id = message.getMessageId() == null ? UUID.randomUUID().toString() : message.getMessageId();
        Intent launch = new Intent(app, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(new Uri.Builder().scheme("openpush-sample").authority("plain").appendPath(id).build());
        PendingIntent tap = PendingIntent.getActivity(app, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(app, CHANNEL_ID) : new Notification.Builder(app);
        try {
            manager.notify("sample.host:" + id, 0, builder.setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(source.getTitle()).setContentText(source.getBody())
                    .setContentIntent(tap).setAutoCancel(true).build());
            app.journal.record(TestJournal.Event.HOST_DISPLAYED);
        } catch (SecurityException | IllegalArgumentException unavailable) {
            app.journal.record(TestJournal.Event.HOST_SUPPRESSED);
        }
    }
}
