package io.openpush.sample;
import android.app.Activity;import android.app.Application;import android.content.Context;import android.os.Bundle;import android.os.SystemClock;import android.service.notification.StatusBarNotification;import android.app.NotificationManager;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;import androidx.test.uiautomator.By;import androidx.test.uiautomator.UiDevice;import androidx.test.uiautomator.Until;import androidx.test.uiautomator.UiObject2;import com.google.firebase.messaging.RemoteMessage;import org.junit.Test;import org.junit.runner.RunWith;import org.json.JSONObject;import java.io.File;import java.io.FileOutputStream;import java.nio.charset.StandardCharsets;import java.util.HashMap;import java.util.Map;import java.util.concurrent.atomic.AtomicInteger;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class NotificationRoutesDeviceTest {
 @Test public void postManualComparisonNotifications() {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();SampleApplication app=(SampleApplication)c.getApplicationContext();
  for(String kind:new String[]{"Play","Article42","App"}) {
   Map<String,String>d=new HashMap<>();d.put("op_v","1");d.put("op_id","manual-direct-"+kind+"-"+System.currentTimeMillis());d.put("op_type","Play".equals(kind)?"url":"Article42".equals(kind)?"deeplink":"app");d.put("op_target","Play".equals(kind)?"https://play.google.com/store/apps/details?id=com.chikh.nano":"Article42".equals(kind)?"openpush://article/read/42":"main");d.put("op_title","OpenPush direct "+kind);d.put("op_body","Local routing comparison test");assertTrue(app.push.showNotification(new RemoteMessage.Builder("qa").setData(d).build()));
  }
 }
 @Test public void tapRoutesWithoutResumingLauncherForExternalUrls() throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SampleApplication app=(SampleApplication)context.getApplicationContext();
  String kind=InstrumentationRegistry.getArguments().getString("routeCase","web");
  AtomicInteger main=new AtomicInteger(),article=new AtomicInteger();
  Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){if(a instanceof MainActivity)main.incrementAndGet();if(a instanceof ArticleActivity)article.incrementAndGet();}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}};
  app.registerActivityLifecycleCallbacks(lifecycle);
  try{
   UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.pressHome();
   Map<String,String>d=new HashMap<>();String id="route-device-"+kind+"-"+System.currentTimeMillis();d.put("op_v","1");d.put("op_id",id);d.put("op_title","OpenPush route QA "+kind);d.put("op_body","Local notification routing test");
   d.put("op_type","deep".equals(kind)?"deeplink":"app".equals(kind)?"app":"url");d.put("op_target","play".equals(kind)?"https://play.google.com/store/apps/details?id=com.chikh.nano":"deep".equals(kind)?"openpush://article/read/42":"app".equals(kind)?"main":"https://www.google.com");
   assertTrue(app.push.showNotification(new RemoteMessage.Builder("qa").setData(d).build()));
   NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);int own=0;for(StatusBarNotification n:nm.getActiveNotifications())if(n.getTag()!=null && n.getTag().equals("openpush:"+id))own++;
   try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"notification-route-"+kind+".json"))){out.write(new JSONObject().put("kind",kind).put("postedBeforeClick",own).put("notificationsEnabled",nm.areNotificationsEnabled()).put("mainResumes",main.get()).put("stage","before-tray-click").toString().getBytes(StandardCharsets.UTF_8));}
   assertEquals("SDK notification must be posted before clicking",1,own);
   device.openNotification();UiObject2 row=device.wait(Until.findObject(By.text("OpenPush route QA "+kind)),5000);assertNotNull("Fresh QA notification must be visible",row);row.click();SystemClock.sleep(2000);
   JSONObject result=new JSONObject().put("kind",kind).put("mainResumes",main.get()).put("articleResumes",article.get()).put("foregroundPackage",device.getCurrentPackageName()).put("source","local SDK notification, not FCM");
   try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"notification-route-"+kind+".json"))){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}
   if("app".equals(kind))assertTrue(main.get()>0);else assertEquals("External/internal destination must not resume launcher",0,main.get());
   if("deep".equals(kind))assertTrue(article.get()>0);
  }finally{app.unregisterActivityLifecycleCallbacks(lifecycle);}
 }
}
