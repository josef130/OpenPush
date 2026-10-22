package io.openpush.sdk;
import android.app.Activity;import android.content.Context;import android.content.Intent;import android.app.NotificationManager;import android.service.notification.StatusBarNotification;import com.google.firebase.messaging.RemoteMessage;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.Robolectric;import org.robolectric.RobolectricTestRunner;import org.robolectric.RuntimeEnvironment;import org.robolectric.Shadows;import org.robolectric.annotation.Config;import java.util.HashMap;import java.util.Map;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class NotificationClickActivityTest {
 @Test public void automaticDefaultNotificationNeedsNoHostClickForwardingAndOpensOnlyOnce()throws Exception{
  android.app.Application app=RuntimeEnvironment.getApplication();
  app.getSharedPreferences("openpush.clicks.v1",0).edit().clear().commit();
  OpenPush push=OpenPush.builder(app,Activity.class,android.R.drawable.ic_dialog_info).build();push.automatic=true;
  assertTrue(push.showNotification(new RemoteMessage.Builder("fixture").addData("op_v","1").addData("op_id","auto-default")
    .addData("op_type","app").addData("op_target","main").addData("op_title","Default").build()));
  NotificationManager manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
  manager.getActiveNotifications()[0].getNotification().contentIntent.send();
  Intent pending=Shadows.shadowOf(app).getNextStartedActivity();
  assertEquals(OpenPushClickActivity.class.getName(),pending.getComponent().getClassName());
  OpenPushClickActivity bridge=Robolectric.buildActivity(OpenPushClickActivity.class,pending).create().get();
  assertEquals(Activity.class.getName(),Shadows.shadowOf(bridge).getNextStartedActivity().getComponent().getClassName());
  OpenPushClickActivity repeated=Robolectric.buildActivity(OpenPushClickActivity.class,new Intent(pending)).create().get();
  assertNull(Shadows.shadowOf(repeated).getNextStartedActivity());
 }
 @Test public void fullStoreUrlUsesDirectStoreIntentAndRepeatedTapDoesNotLaunchAgain(){
  Context c=RuntimeEnvironment.getApplication();
  c.getSharedPreferences("openpush.clicks.v1",0).edit().clear().commit();
  OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info)
      .httpsHost("play.google.com").openUrlsInBrowser().preferGooglePlay().build();
  String url="https://play.google.com/store/apps/details?id=com.chikh.nano";
  Intent click=tap("bridge-full-store-url","url",url);
  OpenPushClickActivity bridge=Robolectric.buildActivity(OpenPushClickActivity.class,click).create().get();
  Intent actual=Shadows.shadowOf(bridge).getNextStartedActivity();
  assertEquals(Intent.ACTION_VIEW,actual.getAction());
  assertEquals("com.android.vending",actual.getPackage());
  assertEquals(url,actual.getDataString());
  assertNull(actual.getSelector());assertNull(actual.getComponent());
  assertFalse(actual.hasCategory(Intent.CATEGORY_BROWSABLE));
  assertNull(Shadows.shadowOf(bridge).getNextStartedActivity());assertTrue(bridge.isFinishing());
  OpenPushClickActivity repeated=Robolectric.buildActivity(OpenPushClickActivity.class,new Intent(click)).create().get();
  assertNull(Shadows.shadowOf(repeated).getNextStartedActivity());assertTrue(repeated.isFinishing());
 }
 private Intent tap(String id,String type,String target){return new Intent().putExtra("op_v","1").putExtra("op_id",id).putExtra("op_type",type).putExtra("op_target",target);}
 @Test public void webTapLaunchesBrowserWithoutLaunchingHostActivity(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("openpush.clicks.v1",0).edit().clear().commit();OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info).httpsHost("example.com").openUrlsInBrowser().build();OpenPushClickActivity bridge=Robolectric.buildActivity(OpenPushClickActivity.class,tap("bridge-web","url","https://example.com")).create().get();Intent actual=Shadows.shadowOf(bridge).getNextStartedActivity();assertEquals(Intent.ACTION_VIEW,actual.getAction());assertEquals("https://example.com",actual.getDataString());assertNull(actual.getComponent());assertNull(actual.getSelector());assertFalse(actual.hasCategory(Intent.CATEGORY_BROWSABLE));assertTrue(bridge.isFinishing());}
 @Test public void storeTapTargetsOfficialStoreWithoutBrowserSelector(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("openpush.clicks.v1",0).edit().clear().commit();OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info).httpsHost("play.google.com").preferGooglePlay().build();OpenPushClickActivity bridge=Robolectric.buildActivity(OpenPushClickActivity.class,tap("bridge-play","url","com.chikh.nano")).create().get();Intent actual=Shadows.shadowOf(bridge).getNextStartedActivity();assertEquals("com.android.vending",actual.getPackage());assertNull(actual.getSelector());assertEquals("https://play.google.com/store/apps/details?id=com.chikh.nano",actual.getDataString());assertTrue(bridge.isFinishing());}
 @Test public void appRouteKeepsHostAndNotificationUsesBridgeForWeb(){Context c=RuntimeEnvironment.getApplication();OpenPush push=OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info).httpsHost("example.com").build();OpenPushClickActivity bridge=Robolectric.buildActivity(OpenPushClickActivity.class,tap("bridge-app","app","main")).create().get();assertEquals(Activity.class.getName(),Shadows.shadowOf(bridge).getNextStartedActivity().getComponent().getClassName());Map<String,String>d=new HashMap<>();d.put("op_v","1");d.put("op_id","bridge-notification");d.put("op_type","url");d.put("op_target","https://example.com");push.showNotification(new RemoteMessage.Builder("test").setData(d).build());NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);for(StatusBarNotification n:nm.getActiveNotifications())if(n.getTag().endsWith("bridge-notification"))assertEquals(OpenPushClickActivity.class.getName(),Shadows.shadowOf(n.getNotification().contentIntent).getSavedIntent().getComponent().getClassName());}
}
