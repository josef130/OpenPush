package io.openpush.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import com.google.firebase.FirebaseApp;
import java.net.URI;

/** Public client configuration only. Installation secrets remain in RegistrationJob. */
final class ClientSettings {
    private static final String PREFS="openpush.setup.v2";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static String resource(Context c,String name){
        int id=c.getResources().getIdentifier(name,"string",c.getPackageName());
        return id==0?"":c.getString(id).trim();
    }
    static String project(Context c){
        final String actual;
        try {actual=FirebaseApp.getInstance().getOptions().getProjectId();}
        catch(IllegalStateException missing){throw new IllegalStateException("OpenPush requires configured Firebase; check google-services.json and Google Services plugin.",missing);}
        if(actual==null || actual.trim().isEmpty())throw new IllegalStateException("OpenPush: Firebase projectId is missing.");
        String expected=resource(c,"openpush_project_id");
        if(!expected.isEmpty()&&!expected.equals(actual))throw new IllegalStateException("OpenPush: Firebase projectId does not match client configuration.");
        return actual;
    }
    static String value(Context c,String name){
        if("openpush_app_id".equals(name)){
            String saved=prefs(c).getString("appId","");
            if(!saved.isEmpty())return saved;
        }
        String configured=resource(c,name);
        if(!configured.isEmpty())return configured;
        switch(name){
            case "openpush_server_url":
                boolean debug=(c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
                return debug&&!BuildConfig.DEBUG_SERVER_URL.isEmpty()?BuildConfig.DEBUG_SERVER_URL:BuildConfig.DEFAULT_SERVER_URL;
            case "openpush_project_id":try{return project(c);}catch(IllegalStateException missing){return "";}
            case "openpush_web_hosts":return "www.google.com,google.com,play.google.com";
            case "openpush_client_key":
                SharedPreferences p=prefs(c);
                if(value(c,"openpush_app_id").equals(p.getString("keyAppId",""))
                    && value(c,"openpush_project_id").equals(p.getString("keyProjectId",""))
                    && value(c,"openpush_server_url").equals(p.getString("keyServer","")))return p.getString("publicClientKey","");
                return "";
            default:return "";
        }
    }
    static boolean validId(String id){return id!=null&&id.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}");}
    static void validateEndpoint(Context c,String endpoint){
        if(endpoint==null||endpoint.isEmpty())throw new IllegalStateException("OpenPush server URL is not configured in this SDK build. Configure a real default endpoint before publishing.");
        try{
            URI u=new URI(endpoint);
            boolean local="127.0.0.1".equals(u.getHost())||"localhost".equals(u.getHost())||"10.0.2.2".equals(u.getHost());
            boolean debug=(c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
            if(!("https".equals(u.getScheme())||(debug&&local&&"http".equals(u.getScheme())))||u.getHost()==null||u.getHost().isEmpty()
                ||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||"/".equals(u.getPath())))throw new Exception();
        }catch(Exception invalid){throw new IllegalStateException("OpenPush server must be an HTTPS origin; loopback HTTP is allowed only in a debug app.");}
    }
    static synchronized void initialize(Context c,String appId){
        if(!validId(appId))throw new IllegalArgumentException("OpenPush APP_ID must be the UUID shown in the dashboard.");
        appId=appId.toLowerCase(java.util.Locale.ROOT);
        String existing=value(c,"openpush_app_id");
        if(!existing.isEmpty()&&!existing.equals(appId))throw new IllegalStateException("OpenPush is already configured for a different APP_ID.");
        project(c);
        if(!CentralTransport.enabled(c))validateEndpoint(c,value(c,"openpush_server_url"));
        if(!appId.equals(prefs(c).getString("appId",""))&&!prefs(c).edit().putString("appId",appId).commit())
            throw new IllegalStateException("OpenPush could not persist APP_ID for background delivery.");
    }
    static void cacheKey(Context c,String appId,String projectId,String key){
        prefs(c).edit().putString("keyAppId",appId).putString("keyProjectId",projectId)
            .putString("keyServer",value(c,"openpush_server_url")).putString("publicClientKey",key).apply();
    }
    static void invalidateKey(Context c){prefs(c).edit().remove("publicClientKey").apply();}
}
