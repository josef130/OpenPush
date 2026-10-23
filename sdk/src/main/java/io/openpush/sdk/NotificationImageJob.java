package io.openpush.sdk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.PersistableBundle;
import android.service.notification.StatusBarNotification;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Best-effort rich image upgrade after the notification has already been posted. */
public final class NotificationImageJob extends JobService {
    private final java.util.Set<JobParameters> cancelled = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<JobParameters, Boolean>());

    static synchronized void schedule(Context context, String tag, String image) {
        if (Build.VERSION.SDK_INT < 23 || !safe(image)) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            StatusBarNotification posted = find(manager, tag);
            if (scheduler == null || posted == null) return;
            PersistableBundle extras = new PersistableBundle();
            extras.putString("tag", tag);
            extras.putString("image", image);
            extras.putLong("postedAt", posted.getPostTime());
            // Do not replace receipt jobs or another image when tags hash to the same value.
            int id = 0x49000000 | (tag.hashCode() & 0xfffff);
            while (BackgroundJobs.pending(scheduler,id) != null) id++;
            scheduler.schedule(new JobInfo.Builder(id, new ComponentName(context, NotificationImageJob.class))
                    .setExtras(extras).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        } catch (RuntimeException ignored) { /* The text notification remains available. */ }
    }

    static boolean safe(String image) {
        if (image == null || image.length() > 2048) return false;
        try {
            URL url = new URL(image);
            return "https".equals(url.getProtocol()) && !url.getHost().isEmpty()
                    && url.getUserInfo() == null && url.getRef() == null && url.getPort() == -1;
        } catch (Exception invalid) { return false; }
    }

    private static StatusBarNotification find(NotificationManager manager, String tag) {
        if (Build.VERSION.SDK_INT < 23) return null;
        for (StatusBarNotification n : manager.getActiveNotifications())
            if (n.getId() == 0 && tag.equals(n.getTag())) return n;
        return null;
    }

    static boolean upgrade(Context context, String tag, long postedAt, Bitmap image) {
        if (Build.VERSION.SDK_INT < 23) return false;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification active = find(manager, tag);
        // Never recreate a notification the user clicked/dismissed, or overwrite a replacement.
        if (active == null || active.getPostTime() != postedAt) return false;
        Notification original = active.getNotification();
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 24) builder = Notification.Builder.recoverBuilder(context, original);
        else builder = new Notification.Builder(context).setSmallIcon(original.getSmallIcon())
                .setContentTitle(original.extras.getCharSequence(Notification.EXTRA_TITLE))
                .setContentText(original.extras.getCharSequence(Notification.EXTRA_TEXT))
                .setContentIntent(original.contentIntent).setAutoCancel(true);
        builder.setStyle(new Notification.BigPictureStyle().bigPicture(image))
                .setOnlyAlertOnce(true);
        manager.notify(tag, 0, builder.build());
        return true;
    }

    @Override public boolean onStartJob(JobParameters params) {
        cancelled.remove(params);
        new Thread(() -> {
            Bitmap image = null;
            try {
                PersistableBundle extras = params.getExtras();
                image = download(extras.getString("image"));
                if (!cancelled.contains(params) && image != null)
                    upgrade(this, extras.getString("tag"), extras.getLong("postedAt"), image);
            } catch (Exception ignored) { /* Image failures do not change delivery or click metrics. */ }
            finally {
                if (image != null) image.recycle();
                if (!cancelled.remove(params)) jobFinished(params, false);
            }
        }, "OpenPush-image").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { cancelled.add(params); return false; }

    private static Bitmap download(String image) throws Exception {
        for (int redirects = 0; redirects <= 3 && safe(image); redirects++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(image).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    image = new URL(new URL(image), connection.getHeaderField("Location")).toString();
                    continue;
                }
                if (status != 200 || connection.getContentLength() > 2 * 1024 * 1024) return null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = connection.getInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (bytes.size() + count > 2 * 1024 * 1024) return null;
                        bytes.write(buffer, 0, count);
                    }
                }
                byte[] data = bytes.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0) return null;
                options.inSampleSize = 1;
                while ((long)(options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize) > 1024 * 1024)
                    options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            } finally { connection.disconnect(); }
        }
        return null;
    }
}
