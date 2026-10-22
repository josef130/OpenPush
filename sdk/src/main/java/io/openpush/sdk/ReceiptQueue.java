package io.openpush.sdk;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import org.json.JSONObject;
import java.net.URL;
import java.util.Map;

/** Opt-in receipts. No FCM token, URL destination or user identity leaves the device. */
final class ReceiptQueue {
    static final Object LOCK = new Object();
    static final String PREFS = "openpush.receipts.v1";
    static final int JOB_ID = 0x4f505243;
    static void uploadStatus(Context context, String status) {
        // Fixed outcomes only: never include payloads, capabilities, token hashes or URLs.
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString("uploadStatus",status).putLong("uploadStatusAt",System.currentTimeMillis()).apply();
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            android.util.Log.d("OpenPushTelemetry",status);
    }
    static void configure(Context context, String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) return;
        try {
            URL url = new URL(endpoint);
            boolean debug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            boolean loopback = "127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost()) || "10.0.2.2".equals(url.getHost());
            if (!("https".equals(url.getProtocol()) || (debug && loopback && "http".equals(url.getProtocol())))
                    || url.getUserInfo()!=null || url.getRef()!=null || url.getQuery()!=null) throw new IllegalArgumentException();
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("endpoint",endpoint).commit();
            schedule(context);
        } catch (Exception invalid) { throw new IllegalArgumentException("Telemetry requires an HTTPS endpoint (debug loopback HTTP allowed)"); }
    }
    static void enqueue(Context context, Map<String,String> data, String event, boolean enabled) {
        enqueue(context, data, event, enabled, true);
    }
    static void enqueue(Context context, Map<String,String> data, String event, boolean enabled, boolean scheduleUpload) {
        if (!enabled) return;
        String token=data.get("op_receipt");
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))return;
        try { synchronized(LOCK) {
            SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String key="event:"+token+":"+event;
            if(prefs.contains(key))return;
            if(prefs.getAll().size()>1000)return;
            JSONObject body=new JSONObject().put("token",token).put("event",event).put("queuedAt",System.currentTimeMillis());
            if(!prefs.edit().putString(key,body.toString()).commit())return;
        } if (scheduleUpload) schedule(context); } catch(RuntimeException | org.json.JSONException ignored) { /* Analytics never break delivery. */ }
    }
    static void enqueueSession(Context context, String deviceTokenHash, long occurredAt, boolean notificationsEnabled) {
        if(deviceTokenHash == null || !deviceTokenHash.matches("[a-f0-9]{64}")) return;
        try { synchronized(LOCK) {
            SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String day=format.format(new java.util.Date(occurredAt));
            String marker=day+":"+notificationsEnabled+":"+deviceTokenHash;
            if(marker.equals(prefs.getString("lastSession","")) || prefs.getAll().size()>1000) return;
            String key="event:session:"+deviceTokenHash+":"+day;
            if(prefs.contains(key) && new JSONObject(prefs.getString(key,"{}")).optLong("occurredAt")>occurredAt) return;
            String installationId=prefs.getString("installationId",null);
            if(installationId==null) { installationId=java.util.UUID.randomUUID().toString(); prefs.edit().putString("installationId",installationId).commit(); }
            JSONObject body=new JSONObject().put("event","session").put("installationId",installationId).put("deviceTokenHash",deviceTokenHash)
                .put("occurredAt",occurredAt).put("notificationsEnabled",notificationsEnabled).put("queuedAt",System.currentTimeMillis());
            // Replace today's pending state with the latest actual foreground observation.
            if(!prefs.edit().putString("event:session:"+deviceTokenHash+":"+day,body.toString()).putString("lastSession",marker).commit()) return;
        } schedule(context); } catch(RuntimeException | org.json.JSONException ignored) { /* Analytics never affect the host UI. */ }
    }
    static void schedule(Context context) {
        boolean hasEvents=false;
        for(String key:context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getAll().keySet())if(key.startsWith("event:")){hasEvents=true;break;}
        if(!hasEvents)return;
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null || scheduler.getPendingJob(JOB_ID)!=null)return;
        try { int result=scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,ReceiptJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
            if(result!=JobScheduler.RESULT_SUCCESS) uploadStatus(context,"schedule_rejected");
        } catch (RuntimeException unavailable) { uploadStatus(context,"schedule_unavailable"); /* Keep the durable queue for a later app start. */ }
    }
}
