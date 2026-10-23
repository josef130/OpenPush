package io.openpush.sdk;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class ClientSettingsTest {
    private static final String ID="11111111-1111-4111-8111-111111111111";
    private Context app(){return RuntimeEnvironment.getApplication();}
    @org.junit.Before @org.junit.After public void clearFirebaseFixture(){
        for(FirebaseApp f:FirebaseApp.getApps(app()))f.delete();
    }
    private void firebase(){FirebaseApp.initializeApp(app(),new FirebaseOptions.Builder()
        .setApplicationId("1:123:android:fixture").setProjectId("fixture-project")
        .setApiKey("AIza"+"A".repeat(35)).build());}
    @Test public void invalidAppIdFailsClearly(){
        try{ClientSettings.initialize(app(),"not-an-id");fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("APP_ID"));}
    }
    @Test public void missingFirebaseDoesNotPretendConfigured(){
        for(FirebaseApp f:FirebaseApp.getApps(app()))f.delete();
        try{ClientSettings.project(app());fail();}catch(IllegalStateException e){assertTrue(e.getMessage().contains("Firebase"));}
    }
    @Test public void readsFirebaseProjectWithoutXml(){firebase();assertEquals("fixture-project",ClientSettings.project(app()));}
    @Test public void missingFirebaseProjectIdFailsClearly(){
        FirebaseApp.initializeApp(app(),new FirebaseOptions.Builder().setApplicationId("1:123:android:fixture")
            .setApiKey("AIza"+"A".repeat(35)).build());
        try{ClientSettings.project(app());fail();}catch(IllegalStateException e){assertTrue(e.getMessage().contains("projectId"));}
    }
    @Test public void appIdCanBeRestoredWithoutXml(){
        ClientSettings.prefs(app()).edit().putString("appId",ID).commit();
        assertEquals(ID,ClientSettings.value(app(),"openpush_app_id"));
        assertEquals("",ClientSettings.resource(app(),"openpush_app_id"));
    }
    @Test public void serverValidationDoesNotAllowReleaseLoopback(){
        app().getApplicationInfo().flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
        try{ClientSettings.validateEndpoint(app(),"http://127.0.0.1:4310");fail();}catch(IllegalStateException expected){}
        ClientSettings.validateEndpoint(app(),"https://example.com");
        for(String invalid:new String[]{"","https://user:pass@example.com","https://example.com/path","http://example.com","https://example.com?secret=x"})
            try{ClientSettings.validateEndpoint(app(),invalid);fail(invalid);}catch(IllegalStateException expected){}
    }
    @Test public void releaseNeverUsesTheDebugServer(){
        app().getApplicationInfo().flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
        assertEquals(BuildConfig.DEFAULT_SERVER_URL,ClientSettings.value(app(),"openpush_server_url"));
        app().getApplicationInfo().flags |= ApplicationInfo.FLAG_DEBUGGABLE;
        assertEquals(BuildConfig.DEBUG_SERVER_URL.isEmpty()?BuildConfig.DEFAULT_SERVER_URL:BuildConfig.DEBUG_SERVER_URL,
            ClientSettings.value(app(),"openpush_server_url"));
    }
    @Test public void bootstrapRejectsMismatchedProjectAndMalformedKey()throws Exception{
        JSONObject request=new JSONObject().put("appId",ID).put("projectId","fixture-project").put("packageName",app().getPackageName());
        JSONObject response=new JSONObject(request.toString()).put("clientKey","a".repeat(64));
        ClientBootstrap.accept(app(),request,response);
        assertEquals("a".repeat(64),ClientSettings.prefs(app()).getString("publicClientKey",""));
        try{ClientBootstrap.accept(app(),request,new JSONObject(response.toString()).put("projectId","wrong"));fail();}catch(IllegalStateException expected){}
        try{ClientBootstrap.accept(app(),request,new JSONObject(response.toString()).put("clientKey","bad"));fail();}catch(IllegalStateException expected){}
    }
}
