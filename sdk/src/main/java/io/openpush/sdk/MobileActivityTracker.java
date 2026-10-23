package io.openpush.sdk;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Foreground app usage only. Message reception and the invisible click bridge never count. */
final class MobileActivityTracker implements Application.ActivityLifecycleCallbacks {
    private static MobileActivityTracker instance;
    private final Application app;
    private MobileActivityTracker(Application app) { this.app=app; }
    static synchronized void configure(Context context, boolean enabled) {
        if(!(context instanceof Application)) return;
        Application app=(Application)context;
        if(instance!=null && (instance.app!=app || !enabled)) {
            instance.app.unregisterActivityLifecycleCallbacks(instance); instance=null;
        }
        if(enabled && instance==null) { instance=new MobileActivityTracker(app); app.registerActivityLifecycleCallbacks(instance); }
    }
    @Override public void onActivityResumed(Activity activity) {
        if(activity instanceof OpenPushClickActivity) return;
        long occurredAt=System.currentTimeMillis();
        NotificationManager manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean enabled=manager!=null && androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()
            && (Build.VERSION.SDK_INT<33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
        if(Build.VERSION.SDK_INT>=26 && manager!=null) {
            NotificationChannel channel=manager.getNotificationChannel(OpenPush.CHANNEL_ID);
            enabled=enabled && (channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
        }
        final boolean notificationsEnabled=enabled;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                try {
                    byte[] digest=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
                    StringBuilder hash=new StringBuilder();
                    for(byte part:digest) hash.append(String.format(Locale.ROOT,"%02x",part & 255));
                    ReceiptQueue.enqueueSession(app,hash.toString(),occurredAt,notificationsEnabled);
                } catch(Exception ignored) { }
            });
        } catch(RuntimeException unavailable) { /* Firebase may not be configured yet. */ }
    }
    public void onActivityCreated(Activity activity,Bundle state) { }
    public void onActivityStarted(Activity activity) { }
    public void onActivityPaused(Activity activity) { }
    public void onActivityStopped(Activity activity) { }
    public void onActivitySaveInstanceState(Activity activity,Bundle state) { }
    public void onActivityDestroyed(Activity activity) { }
}
