package io.openpush.sample;
import android.app.NotificationManager;import android.app.PendingIntent;import android.service.notification.StatusBarNotification;import android.content.Context;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;import com.google.firebase.messaging.RemoteMessage;import org.junit.Test;import org.junit.runner.RunWith;import java.util.HashMap;import java.util.Map;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TelemetryDeviceTest {
 @Test public void postsTwoNotificationsAndClicksFirstTwice() throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  SampleApplication app=(SampleApplication)context.getApplicationContext();
  assertTrue("Explicit QA endpoint must be configured",BuildConfig.TELEMETRY_ENDPOINT.endsWith(":4311/api/events"));
  for(String suffix:new String[]{"one","two"}){
   Map<String,String>d=new HashMap<>();d.put("op_v","1");d.put("op_id","telemetry-device-"+suffix);d.put("op_type","app");d.put("op_target","main");d.put("op_title","OpenPush receipt protocol QA "+suffix);d.put("op_body","Local device test; not an FCM delivery claim");d.put("op_receipt",("one".equals(suffix)?"a":"b").repeat(43));
   assertTrue(app.push.showNotification(new RemoteMessage.Builder("qa").setData(d).build()));
  }
  NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  PendingIntent click=null;int count=0;
  for(StatusBarNotification n:manager.getActiveNotifications())if(n.getTag()!=null&&n.getTag().startsWith("openpush:telemetry-device-")){count++;if(n.getTag().endsWith("one"))click=n.getNotification().contentIntent;}
  assertEquals(2,count);assertNotNull(click);
  click.send();InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  click.send();InstrumentationRegistry.getInstrumentation().waitForIdleSync();
 }
}
