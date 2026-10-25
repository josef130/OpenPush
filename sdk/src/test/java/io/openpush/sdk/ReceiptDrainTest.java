package io.openpush.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class ReceiptDrainTest {
    private Context context() {
        Context c=RuntimeEnvironment.getApplication();
        c.getSharedPreferences(ReceiptQueue.PREFS,0).edit().clear().commit();
        return c;
    }
    private Map<String,String> data() { return Collections.singletonMap("op_receipt","f".repeat(43)); }

    @Test public void displayedAddedDuringReceivedUploadDrainsWithoutWaitingForClickOrRetry() {
        Context c=context(); List<String> uploaded=new ArrayList<>();
        ReceiptQueue.enqueue(c,data(),"received",true,false);
        assertFalse(ReceiptJobService.drain(c,()->true,body->{
            String event=body.getString("event");uploaded.add(event);
            if("received".equals(event)) ReceiptQueue.enqueue(c,data(),"displayed",true,false);
            return 200;
        }));
        assertEquals(Arrays.asList("received","displayed"),uploaded);
        assertFalse(c.getSharedPreferences(ReceiptQueue.PREFS,0).getAll().keySet().stream().anyMatch(k->k.startsWith("event:")));
        ReceiptQueue.enqueue(c,data(),"clicked",true,false);
        ReceiptJobService.drain(c,()->true,body->{uploaded.add(body.getString("event"));return 200;});
        assertEquals(Arrays.asList("received","displayed","clicked"),uploaded);
    }

    @Test public void transportFailureKeepsEventsForDurableRetry() {
        Context c=context(); ReceiptQueue.enqueue(c,data(),"displayed",true,false);
        assertTrue(ReceiptJobService.drain(c,()->true,body->503));
        assertTrue(c.getSharedPreferences(ReceiptQueue.PREFS,0).contains("event:"+data().get("op_receipt")+":displayed"));
    }

    @Test public void offlineLegacyEndpointCannotBlockCentralReceipt() throws Exception {
        Context c=context();SharedPreferences p=c.getSharedPreferences(ReceiptQueue.PREFS,0);
        long now=System.currentTimeMillis();
        p.edit().putString("event:legacy",new JSONObject().put("event","received").put("queuedAt",now).toString())
            .putString("event:central",new JSONObject().put("event","displayed").put("transport","firestore").put("queuedAt",now).toString()).commit();
        List<String> uploaded=new ArrayList<>();
        assertTrue(ReceiptJobService.drain(c,()->true,body->{
            if(!"firestore".equals(body.optString("transport")))throw new java.io.IOException("offline");
            uploaded.add(body.getString("event"));return 200;
        }));
        assertEquals(Collections.singletonList("displayed"),uploaded);
        assertTrue(p.contains("event:legacy"));assertFalse(p.contains("event:central"));
    }

    @Test public void newerSessionIsNotRemovedByAnOlderUpload() throws Exception {
        Context c=context(); SharedPreferences prefs=c.getSharedPreferences(ReceiptQueue.PREFS,0);
        String key="event:session:test";
        prefs.edit().putString(key,new JSONObject().put("event","session").put("version",1).put("queuedAt",System.currentTimeMillis()).toString()).commit();
        List<Integer> versions=new ArrayList<>();
        assertFalse(ReceiptJobService.drain(c,()->true,body->{
            int version=body.getInt("version");versions.add(version);
            if(version==1) prefs.edit().putString(key,new JSONObject().put("event","session").put("version",2).put("queuedAt",System.currentTimeMillis()).toString()).commit();
            return 200;
        }));
        assertEquals(Arrays.asList(1,2),versions);
    }
}
