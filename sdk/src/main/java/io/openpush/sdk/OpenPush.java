package io.openpush.sdk;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import com.google.firebase.messaging.RemoteMessage;
import io.openpush.core.Route;
import io.openpush.core.ClickLedger;
import io.openpush.core.RoutingEngine;
import java.util.HashMap;
import java.util.Map;

/** Java SDK with explicit builder integration or resource-configured automatic setup. */
public final class OpenPush {
    private static volatile OpenPush notificationInstance;
    boolean automatic;
    /** Select the dashboard app once; Firebase project and enrollment are automatic. */
    public static void init(Activity activity,String appId) {
        ClientSettings.initialize(activity.getApplicationContext(),appId);
        init(activity);
    }
    /** Declarative setup; safe to call again from MainActivity after process recreation. */
    public static void init(Activity activity) {
        OpenPush push=AutoSetup.get(activity);
        if(push==null)throw new IllegalStateException("OpenPush configuration missing; see Android SDK setup");
        AutoSetup.register(activity);
        push.handleIntent(activity,activity.getIntent());
        if(Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            android.content.SharedPreferences prefs=activity.getSharedPreferences("openpush.permission.v1",Context.MODE_PRIVATE);
            if(!prefs.getBoolean("requested",false)){
                prefs.edit().putBoolean("requested",true).apply();
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},0x4f50);
            }
        }
    }
    /** Existing host FCM services delegate here instead of registering another receiver. */
    public static boolean receive(Context context,RemoteMessage message){
        OpenPush push=AutoSetup.get(context);
        if(push==null)push=notificationInstance;
        return push!=null && push.showNotification(message);
    }
    public static void refreshRegistration(Context context){AutoSetup.register(context);}
    public static final String CHANNEL_ID = "openpush";
    private static final Object CLICK_LOCK = new Object();
    private final Context context;
    private final Class<? extends Activity> activity;
    private final int smallIcon;
    private final Router router;
    private final Diagnostics diagnostics;
    private final PipelineDiagnostics pipeline;
    private final boolean telemetryEnabled;
    public interface PipelineDiagnostics { void onReport(PipelineReport report); }

    public interface RouteHandler extends RoutingEngine.Handler<Activity> {
        /** Pure allowlist check: do not navigate or perform side effects here. */
        boolean accepts(Route route);
        /** Called on main thread, after the durable claim. */
        void open(Activity activity, Route route);
    }

    public enum ClickResult { IGNORED, REJECTED, DUPLICATE, STORAGE_FAILED, OPENED, HANDLER_FAILED }
    public enum Event { INVALID_MESSAGE, PERMISSION_DENIED, NOTIFICATIONS_DISABLED, CHANNEL_DISABLED,
        DISPLAYED, DISPLAY_FAILED, INVALID_INTENT, REJECTED, DUPLICATE, STORAGE_FAILED, OPENED, HANDLER_FAILED }
    /** Events contain no token, payload, URL, ID, argument, or exception text. Default: no logging. */
    public interface Diagnostics { void onEvent(Event event); }

    public static Builder builder(Context context, Class<? extends Activity> activity, int icon) {
        return new Builder(context, activity, icon);
    }

    public static final class Builder {
        private final Context context;
        private final Class<? extends Activity> activity;
        private final int icon;
        private final Router.Builder rules;
        private Diagnostics diagnostics = event -> { };
        private PipelineDiagnostics pipeline = report -> { };
        private String telemetryEndpoint;
        private boolean trackMonthlyActiveUsers;
        public Builder trackMonthlyActiveUsers(boolean enabled) { trackMonthlyActiveUsers=enabled; return this; }
        public Builder telemetryEndpoint(String endpoint) { this.telemetryEndpoint = endpoint; return this; }
        public Builder pipelineDiagnostics(PipelineDiagnostics listener) {
            if (listener == null) throw new IllegalArgumentException("Listener required");
            pipeline = listener; return this;
        }
        private Builder(Context context, Class<? extends Activity> activity, int icon) {
            this.context = context; this.activity = activity; this.icon = icon;
            rules = new Router.Builder(context);
        }
        public Builder screen(String name, RouteHandler handler) { rules.screen(name, handler); return this; }
        public Builder httpsHost(String host) { rules.httpsHost(host); return this; }
        public Builder preferGooglePlay() { rules.preferGooglePlay(); return this; }
        public Builder openUrlsInBrowser() { rules.openUrlsInBrowser(); return this; }
        public Builder httpHost(String host) { rules.httpHost(host); return this; }
        public Builder launchDiagnostics(Router.LaunchDiagnostics listener) { rules.launchDiagnostics(listener); return this; }
        public Builder deepLink(String scheme, String host, String path, Class<? extends Activity> destination) {
            rules.deepLink(scheme, host, path, destination); return this;
        }
        public Builder action(String type, RouteHandler handler) { rules.action(type, handler); return this; }
        public Builder diagnostics(Diagnostics diagnostics) {
            if (diagnostics == null) throw new IllegalArgumentException("Diagnostics required");
            this.diagnostics = diagnostics; return this;
        }
        public OpenPush build() {
            if(trackMonthlyActiveUsers && (telemetryEndpoint==null || telemetryEndpoint.isEmpty())) throw new IllegalArgumentException("MAU tracking requires a telemetry endpoint");
            return new OpenPush(context, activity, icon, rules.build(), diagnostics, pipeline, telemetryEndpoint, trackMonthlyActiveUsers);
        }
    }

    public OpenPush(Context context, Class<? extends Activity> activity, int smallIcon) {
        this(context, activity, smallIcon, null, event -> { }, report -> { }, null, false);
    }

    private OpenPush(Context context, Class<? extends Activity> activity, int smallIcon,
                     Router router, Diagnostics diagnostics, PipelineDiagnostics pipeline, String telemetryEndpoint, boolean trackMonthlyActiveUsers) {
        if (smallIcon == 0) throw new IllegalArgumentException("A notification small icon is required");
        if (context == null || activity == null) throw new IllegalArgumentException("Host context/activity required");
        this.context = context.getApplicationContext();
        this.activity = activity;
        this.smallIcon = smallIcon;
        this.router = router;
        this.diagnostics = diagnostics;
        this.pipeline = pipeline;
        this.telemetryEnabled = telemetryEndpoint != null && !telemetryEndpoint.isEmpty();
        if(telemetryEnabled) ReceiptQueue.configure(this.context,telemetryEndpoint);
        MobileActivityTracker.configure(this.context, telemetryEnabled && trackMonthlyActiveUsers);
        notificationInstance = this;
    }

    public void createChannel(String name) {
        if (Build.VERSION.SDK_INT >= 26) {
            manager().createNotificationChannel(new NotificationChannel(CHANNEL_ID, name,
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    /** True means owned by OpenPush, even if malformed, disabled or permission denied. */
    public boolean showNotification(RemoteMessage message) {
        Map<String, String> data = message.getData();
        // Queue reception first, but schedule only after the synchronous display outcome
        // is durable. Otherwise a receipt job can snapshot only received and defer displayed.
        ReceiptQueue.enqueue(context,data,"received",telemetryEnabled,false);
        Route route = Route.parse(data);
        trace("RECEIVED", data, data.keySet(), route, !data.containsKey("op_v") ? "HOST_OWNED" : route == null ? "INVALID" : "SDK_OWNED");
        if (!data.containsKey("op_v")) { if (telemetryEnabled) ReceiptQueue.schedule(context); return false; }
        if (route == null) { receipt(data,"display_failed"); emit(Event.INVALID_MESSAGE); return true; }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) { receipt(data,"notification_disabled"); emit(Event.PERMISSION_DENIED); return true; }
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) { receipt(data,"notification_disabled"); emit(Event.NOTIFICATIONS_DISABLED); return true; }
        if (Build.VERSION.SDK_INT >= 26 && manager().getNotificationChannel(CHANNEL_ID) == null) {
            createChannel("OpenPush");
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager().getNotificationChannel(CHANNEL_ID);
            if (channel == null) { receipt(data,"display_failed"); emit(Event.DISPLAY_FAILED); return true; }
            if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                receipt(data,"notification_disabled"); emit(Event.CHANNEL_DISABLED); return true;
            }
        }
        Intent intent = new Intent(context, (automatic || "url".equals(route.type) || "deeplink".equals(route.type)) ? OpenPushClickActivity.class : activity)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(new Uri.Builder().scheme("openpush").authority("click").appendPath(route.id).build());
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getKey().startsWith("op_")) intent.putExtra(entry.getKey(), entry.getValue());
        }
        trace("PENDING_INTENT", data, data.keySet(), route, ("url".equals(route.type) || "deeplink".equals(route.type))
                ? "DIRECT_ACTIVITY_UNIQUE_ID_UPDATE_CURRENT_IMMUTABLE" : "HOST_ACTIVITY_UNIQUE_ID_UPDATE_CURRENT_IMMUTABLE");
        PendingIntent click = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        RemoteMessage.Notification source = message.getNotification();
        String title = source == null ? data.get("op_title") : source.getTitle();
        String body = source == null ? data.get("op_body") : source.getBody();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(smallIcon).setContentTitle(title == null ? "" : title)
                .setContentText(body == null ? "" : body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(click).setAutoCancel(true);
        try {
            manager().notify("openpush:" + route.id, 0, builder.build());
            receipt(data,"displayed");
            emit(Event.DISPLAYED);
            // Publish immediately; optional image loading never delays the display receipt.
            NotificationImageJob.schedule(context, "openpush:" + route.id, data.get("op_image"));
        } catch (SecurityException | IllegalArgumentException unavailable) { receipt(data,"display_failed"); emit(Event.DISPLAY_FAILED); }
        return true;
    }

    static void dispatchNotificationClick(Activity bridge, Intent source) {
        AutoSetup.get(bridge);
        OpenPush push = notificationInstance;
        if(push == null) {
            Intent launcher = bridge.getPackageManager().getLaunchIntentForPackage(bridge.getPackageName());
            if(launcher != null) {
                if(source != null && source.getExtras() != null) launcher.putExtras(source.getExtras());
                bridge.startActivity(launcher);
            }
            return;
        }
        String type = source == null ? null : source.getStringExtra("op_type");
        if("url".equals(type) || "deeplink".equals(type) || push.automatic) {
            ClickResult result = push.handleIntent(bridge,source);
            if(result == ClickResult.DUPLICATE || (result == ClickResult.OPENED && !"app".equals(type))) return;
        }
        Intent launcher = new Intent(push.context,push.activity)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(source != null) { launcher.setData(source.getData()); if(source.getExtras()!=null)launcher.putExtras(source.getExtras()); }
        bridge.startActivity(launcher);
    }

    public ClickResult handleIntent(Activity host, Intent intent) { return dispatch(host, intent, null); }

    /** Call in onCreate AND onNewIntent when the host is ready to navigate. Single process only. */
    public ClickResult handleIntent(Activity host, Intent intent, RouteHandler handler) {
        return dispatch(host, intent, handler);
    }

    private ClickResult dispatch(Activity host, Intent intent, RouteHandler legacyHandler) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Main thread required");
        Map<String, String> data = new HashMap<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        try {
          if (intent == null || intent.getExtras() == null) {
              trace("CLICK", data, keys, null, "IGNORED_NO_PROTOCOL");
              return ClickResult.IGNORED;
          }
          Bundle extras = intent.getExtras();
          for (String key : extras.keySet()) {
            keys.add(key);
            if (key.startsWith("op_")) {
                Object value = extras.get(key);
                if (value instanceof String) data.put(key, (String) value);
            }
          }
        } catch (RuntimeException malformed) {
            emit(Event.INVALID_INTENT);
            return ClickResult.REJECTED;
        }
        Route route = Route.parse(data);
        trace("CLICK", data, keys, route, !data.containsKey("op_v") ? "IGNORED_NO_PROTOCOL" : route == null ? "INVALID" : "PARSED");
        receipt(data,"clicked");
        if (!data.containsKey("op_v")) return ClickResult.IGNORED;
        RoutingEngine.Handler<Activity> handler;
        try {
            handler = legacyHandler != null ? (route != null && legacyHandler.accepts(route) ? legacyHandler : null)
                    : (router == null ? null : router.resolve(route));
        } catch (RuntimeException failed) { emit(Event.HANDLER_FAILED); return ClickResult.HANDLER_FAILED; }
        trace("RESOLVED", data, keys, route, handler == null ? "NO_HANDLER" : "HANDLER_FOUND");
        if (route == null || handler == null) { emit(Event.REJECTED); return ClickResult.REJECTED; }
        try { synchronized (CLICK_LOCK) {
            SharedPreferences prefs = context.getSharedPreferences("openpush.clicks.v1", Context.MODE_PRIVATE);
            ClickLedger.Claim claim = new ClickLedger(new ClickLedger.Store() {
                public String read() { return prefs.getString("ids", ""); }
                public boolean save(String ids) { return prefs.edit().putString("ids", ids).commit(); }
            }, 128).claim(route.id);
            if (claim == ClickLedger.Claim.DUPLICATE) { trace("CLAIM", data, keys, route, "DUPLICATE"); emit(Event.DUPLICATE); return ClickResult.DUPLICATE; }
            if (claim == ClickLedger.Claim.STORAGE_FAILED) { emit(Event.STORAGE_FAILED); return ClickResult.STORAGE_FAILED; }
        } } catch (RuntimeException failed) { emit(Event.STORAGE_FAILED); return ClickResult.STORAGE_FAILED; }
        trace("DISPATCH", data, keys, route, "CLAIMED");
        try { handler.open(host, route); }
        catch (RuntimeException failed) { emit(Event.HANDLER_FAILED); return ClickResult.HANDLER_FAILED; }
        emit(Event.OPENED);
        return ClickResult.OPENED;
    }

    private void receipt(Map<String,String> data,String event) { ReceiptQueue.enqueue(context,data,event,telemetryEnabled); }

    private void trace(String stage, Map<String, String> data, java.util.Set<String> keys, Route route, String decision) {
        try { pipeline.onReport(new PipelineReport(stage, data, keys, route, decision)); }
        catch (RuntimeException ignored) { /* Diagnostics cannot affect routing. */ }
    }

    private void emit(Event event) {
        try { diagnostics.onEvent(event); } catch (RuntimeException ignored) { /* Diagnostics cannot break delivery. */ }
    }

    private NotificationManager manager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
