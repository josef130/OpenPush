package io.openpush.sample;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

/** QA counters only: no token, URI, ID, message body, or argument values are stored/logged. */
public final class TestJournal {
    public enum Event { FCM_CALLBACK, LOCAL_SIMULATION, SDK_OWNED, HOST_DATA, HOST_DISPLAYED,
        HOST_SUPPRESSED, TOKEN_CALLBACK, DELETED_CALLBACK, OPENED, DUPLICATE, REJECTED, HANDLER_FAILED }
    private final SharedPreferences prefs;
    public TestJournal(Context context) { prefs = context.getSharedPreferences("openpush.sample.qa", Context.MODE_PRIVATE); }
    public synchronized void record(Event event) {
        prefs.edit().putInt(event.name(), count(event) + 1).apply();
    }
    /** Debug-only bounded pipeline evidence: hashes and fixed decisions, never payload/token/URL. */
    public synchronized void recordPipeline(String stage, String idFingerprint, String decision) {
        if(!BuildConfig.DEBUG)return;
        try {
            org.json.JSONArray old=new org.json.JSONArray(prefs.getString("pipeline", "[]"));
            org.json.JSONArray recent=new org.json.JSONArray();
            for(int n=Math.max(0,old.length()-23);n<old.length();n++)recent.put(old.get(n));
            recent.put(new org.json.JSONObject().put("at",System.currentTimeMillis()).put("stage",stage).put("id",idFingerprint).put("decision",decision));
            prefs.edit().putString("pipeline",recent.toString()).commit();
        } catch(RuntimeException | org.json.JSONException ignored) { }
    }
    public int count(Event event) { return prefs.getInt(event.name(), 0); }
    public String summary() {
        List<Event> fields = Arrays.asList(Event.FCM_CALLBACK, Event.LOCAL_SIMULATION, Event.SDK_OWNED,
                Event.HOST_DISPLAYED, Event.HOST_DATA, Event.OPENED, Event.DUPLICATE, Event.REJECTED,
                Event.HANDLER_FAILED, Event.TOKEN_CALLBACK, Event.DELETED_CALLBACK);
        StringBuilder summary = new StringBuilder();
        for (Event event : fields) summary.append(event.name()).append(": ").append(count(event)).append('\n');
        return summary.toString();
    }
}
