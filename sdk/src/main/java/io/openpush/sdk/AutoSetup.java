package io.openpush.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import java.net.URL;

/** Declarative configuration is restored in a cold FCM process, before any Activity exists. */
final class AutoSetup implements Application.ActivityLifecycleCallbacks {
    private static Context owner;
    private static OpenPush instance;
    static String setting(Context c,String name) {
        int id=c.getResources().getIdentifier(name,"string",c.getPackageName());
        return id==0?"":c.getString(id).trim();
    }
    static synchronized OpenPush get(Context input) {
        Context c=input.getApplicationContext();
        if(owner==c && instance!=null)return instance;
        String endpoint=setting(c,"openpush_server_url");
        if(endpoint.isEmpty())return null; // Existing explicit builder integrations remain unchanged.
        try {
            URL server=new URL(endpoint);
            boolean debug=(c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
            boolean loopback="127.0.0.1".equals(server.getHost())||"localhost".equals(server.getHost())||"10.0.2.2".equals(server.getHost());
            if(!("https".equals(server.getProtocol())||(debug&&loopback&&"http".equals(server.getProtocol())))
                    || server.getUserInfo()!=null || server.getQuery()!=null || server.getRef()!=null
                    || !(server.getPath().isEmpty()||"/".equals(server.getPath())))throw new IllegalArgumentException();
            if(!setting(c,"openpush_app_id").matches("[a-fA-F0-9-]{36}") || !setting(c,"openpush_client_key").matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
            FirebaseApp firebase=FirebaseApp.getInstance();
            if(!setting(c,"openpush_project_id").equals(firebase.getOptions().getProjectId()))throw new IllegalArgumentException();
            Intent launch=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            if(launch==null || launch.getComponent()==null)throw new IllegalArgumentException();
            android.content.pm.ActivityInfo info=c.getPackageManager().getActivityInfo(launch.getComponent(),0);
            Class<? extends Activity> activity=Class.forName(info.targetActivity==null?info.name:info.targetActivity).asSubclass(Activity.class);
            int icon=c.getResources().getIdentifier("openpush_notification_icon","drawable",c.getPackageName());
            if(icon==0)icon=c.getResources().getIdentifier("ic_notification","drawable",c.getPackageName());
            if(icon==0)throw new IllegalArgumentException();
            OpenPush.Builder builder=OpenPush.builder(c,activity,icon).preferGooglePlay()
                .telemetryEndpoint(base(c)+"/api/events").trackMonthlyActiveUsers(true);
            for(String host:setting(c,"openpush_web_hosts").split(","))if(!host.trim().isEmpty())builder.httpsHost(host.trim());
            builder.httpsHost("play.google.com");
            int links=c.getResources().getIdentifier("openpush_deep_links","array",c.getPackageName());
            if(links!=0)for(String rule:c.getResources().getStringArray(links)){
                String[] parts=rule.split("\\|",-1);if(parts.length!=4)throw new IllegalArgumentException();
                String destination=parts[3].startsWith(".")?c.getPackageName()+parts[3]:parts[3];
                builder.deepLink(parts[0],parts[1],parts[2],Class.forName(destination).asSubclass(Activity.class));
            }
            instance=builder.build();instance.automatic=true;owner=c;
            instance.createChannel("Notifications");
            if(c instanceof Application)((Application)c).registerActivityLifecycleCallbacks(new AutoSetup());
            int others=0;
            for(ResolveInfo r:c.getPackageManager().queryIntentServices(new Intent("com.google.firebase.MESSAGING_EVENT").setPackage(c.getPackageName()),PackageManager.GET_RESOLVED_FILTER))
                if(r.serviceInfo!=null && !OpenPushMessagingService.class.getName().equals(r.serviceInfo.name)
                        && !"com.google.firebase.messaging.FirebaseMessagingService".equals(r.serviceInfo.name))others++;
            if(others>0)Log.w("OpenPushSetup","HOST_MESSAGING_SERVICE_REQUIRES_DELEGATION");
            return instance;
        } catch(Exception unavailable) {
            Log.w("OpenPushSetup","CONFIGURATION_UNAVAILABLE");return null;
        }
    }
    static String base(Context c){return setting(c,"openpush_server_url").replaceAll("/+$","");}
    static void register(Context c){ if(get(c)!=null)RegistrationJob.schedule(c); }
    public void onActivityResumed(Activity a){register(a);}
    public void onActivityCreated(Activity a,Bundle state){
        if(!(a instanceof OpenPushClickActivity) && instance!=null)instance.handleIntent(a,a.getIntent());
    }
    public void onActivityStarted(Activity a){} public void onActivityPaused(Activity a){}
    public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){}
}
