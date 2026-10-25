package io.openpush.sdk;

import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public final class ReceiptJobService extends JobService {
    private volatile int generation;
    interface BooleanSupplier { boolean getAsBoolean(); }
    interface Uploader { int upload(JSONObject body) throws Exception; }

    // Refresh the queue after every upload: display/click/session events can arrive
    // while the worker is in a network call. Never defer those solely due to a snapshot.
    static boolean drain(Context context, BooleanSupplier active, Uploader uploader) {
        SharedPreferences prefs=context.getSharedPreferences(ReceiptQueue.PREFS,MODE_PRIVATE);
        java.util.Set<String> attempted=new java.util.HashSet<>();
        for (int processed=0; processed<20 && active.getAsBoolean(); processed++) {
            Map.Entry<String,?> entry=null;
            synchronized(ReceiptQueue.LOCK) {
                for(Map.Entry<String,?> candidate:prefs.getAll().entrySet())
                    if(candidate.getKey().startsWith("event:") && !attempted.contains(candidate.getKey())) { entry=candidate; break; }
                if(entry==null) {
                    for(String key:prefs.getAll().keySet())if(key.startsWith("event:"))return true;
                    return false;
                }
                attempted.add(entry.getKey());
            }
            try {
                JSONObject body=new JSONObject((String)entry.getValue());
                int code=200;
                if(System.currentTimeMillis()-body.getLong("queuedAt")<=30L*86400000) {
                    body.remove("queuedAt");
                    code=uploader.upload(body);
                    ReceiptQueue.uploadStatus(context,"http_"+code);
                }
                if(code!=200 && code!=400 && code!=404) continue;
                synchronized(ReceiptQueue.LOCK) {
                    // A newer session can replace this key during upload. Keep that version.
                    if(entry.getValue().equals(prefs.getString(entry.getKey(),null))) {
                        SharedPreferences.Editor edit=prefs.edit().remove(entry.getKey());
                        if(code!=200 && "session".equals(body.optString("event"))) edit.remove("lastSession");
                        edit.commit();
                    } else attempted.remove(entry.getKey());
                }
            } catch(Exception unavailable) {
                ReceiptQueue.uploadStatus(context,"transport_unavailable");
                // Retain failed events, but do not let a legacy offline endpoint
                // block unrelated receipts using the new central transport.
            }
        }
        synchronized(ReceiptQueue.LOCK) {
            for(String key:prefs.getAll().keySet()) if(key.startsWith("event:")) return true;
        }
        return false;
    }

    @Override public boolean onStartJob(JobParameters params) {
        final int current=++generation;
        new Thread(()->{
            boolean retry=drain(this,()->current==generation,body->{
                if("firestore".equals(body.optString("transport")))return CentralTransport.client(this).receipt(body);
                String endpoint=body.optString("uploadEndpoint",getSharedPreferences(ReceiptQueue.PREFS,MODE_PRIVATE).getString("endpoint",""));
                body.remove("uploadEndpoint");
                HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
                try {
                    connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                    connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");
                    connection.setRequestProperty("X-OpenPush-Client","dashboard");connection.setDoOutput(true);
                    byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
                    try(java.io.OutputStream out=connection.getOutputStream()){out.write(bytes);}
                    return connection.getResponseCode();
                } finally { connection.disconnect(); }
            });
            synchronized(ReceiptQueue.LOCK) {
                SharedPreferences prefs=getSharedPreferences(ReceiptQueue.PREFS,MODE_PRIVATE);
                for(String key:prefs.getAll().keySet()) if(key.startsWith("event:")) retry=true;
                if(current==generation)jobFinished(params,retry);
            }
        },"OpenPush-receipts").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){generation++;return true;}
}
