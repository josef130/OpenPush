package io.openpush.sdk;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CentralTransportTest {
    static final String ID="11111111-1111-4111-8111-111111111111";
    Context c;
    @Before public void setup() {
        c=RuntimeEnvironment.getApplication();
        for(FirebaseApp app:FirebaseApp.getApps(c))app.delete();
        CentralTransport.authFile(c).delete();
        ClientSettings.prefs(c).edit().clear().putString("appId",ID).commit();
        c.getSharedPreferences(ReceiptQueue.PREFS,0).edit().clear().commit();
        FirebaseApp.initializeApp(c,new FirebaseOptions.Builder().setProjectId("host-project")
            .setApplicationId("1:123:android:host").setApiKey("AIza"+"A".repeat(35)).build());
    }
    @After public void cleanup(){CentralTransport.authFile(c).delete();for(FirebaseApp app:FirebaseApp.getApps(c))app.delete();}
    @Test public void secondaryFirebaseNeverReplacesHostDefault(){
        assertTrue(CentralTransport.enabled(c));
        assertEquals("openpush-604a1",CentralTransport.secondary(c).getOptions().getProjectId());
        assertEquals("host-project",ClientSettings.project(c));
        c.getApplicationInfo().flags &= ~android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE;
        ClientSettings.initialize(c,ID); // Release does not need a localhost endpoint.
    }
    @Test public void registerUsesIsolatedIdentityAndRealHostMetadata() throws Exception {
        List<String> calls=new ArrayList<>();
        CentralTransport.Client client=new CentralTransport.Client(c,(url,method,body,bearer,type)->{
            calls.add(url);
            if(url.contains("accounts:signUp"))return new CentralTransport.Reply(200,new JSONObject()
                .put("localId","ownUid").put("idToken","test-id").put("refreshToken","test-refresh").put("expiresIn","3600"));
            assertEquals("test-id",bearer);
            if(method.equals("GET"))return new CentralTransport.Reply(200,new JSONObject().put("fields",new JSONObject()
                .put("projectId",new JSONObject().put("stringValue","host-project"))
                .put("packageName",new JSONObject().put("stringValue",c.getPackageName()))));
            JSONObject fields=new JSONObject(body).getJSONArray("writes").getJSONObject(0).getJSONObject("update").getJSONObject("fields");
            assertEquals("host-project",fields.getJSONObject("projectId").getString("stringValue"));
            assertEquals("0",fields.getJSONObject("lastOpenedAt").getString("integerValue"));
            return new CentralTransport.Reply(200,new JSONObject());
        },CentralTransport.marker(),"public-test-key");
        client.register("host-fcm-token",true);
        client.register("host-fcm-token",true);
        assertEquals(4,calls.size()); // sign-up + profile + commit, then profile only.
        assertEquals("host-project",FirebaseApp.getInstance().getOptions().getProjectId());
    }
    @Test public void challengeMustMatchOwnUidAndApp()throws Exception {
        CentralTransport.saveAuth(c,new JSONObject().put("appId",ID).put("uid","ownUid"));
        Map<String,String> d=new HashMap<>();d.put("op_enroll_v","1");d.put("op_enroll_app",ID);
        d.put("op_enroll_nonce","x".repeat(43));d.put("op_enroll_uid","anotherUid");
        assertTrue(CentralTransport.challenge(c,d));assertEquals("",ClientSettings.prefs(c).getString("centralProof",""));
        d.put("op_enroll_uid","ownUid");assertTrue(CentralTransport.challenge(c,d));
        assertEquals("x".repeat(43),ClientSettings.prefs(c).getString("centralProof",""));
    }
    @Test public void migrationKeepsLegacyQueuedEventsAtOriginalServer()throws Exception {
        ReceiptQueue.configure(c,"https://old.example/api/events");
        Map<String,String>d=new HashMap<>();d.put("op_receipt","x".repeat(43));
        ReceiptQueue.enqueue(c,d,"displayed",true,false);
        ReceiptQueue.configure(c,CentralTransport.marker());
        JSONObject body=new JSONObject(c.getSharedPreferences(ReceiptQueue.PREFS,0).getString("event:"+d.get("op_receipt")+":displayed","{}"));
        assertEquals("https://old.example/api/events",body.getString("uploadEndpoint"));
        assertFalse(body.has("transport"));
    }
}
