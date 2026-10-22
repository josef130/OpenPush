package io.openpush.sdk;
import android.app.Activity;import android.content.Context;import com.google.firebase.messaging.RemoteMessage;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.RuntimeEnvironment;import org.robolectric.annotation.Config;import java.util.HashMap;import java.util.Map;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class ReceiptQueueTest {
 @Test public void imageMessageConfirmsDisplayBeforeAnyClickOrImageDownload(){
  Context c=RuntimeEnvironment.getApplication();android.content.SharedPreferences prefs=c.getSharedPreferences(ReceiptQueue.PREFS,0);prefs.edit().clear().commit();
  OpenPush push=OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info).telemetryEndpoint("https://example.com/api/events").build();
  Map<String,String> d=new HashMap<>();d.put("op_v","1");d.put("op_id","image-receipt-test");d.put("op_type","app");d.put("op_target","main");d.put("op_receipt","e".repeat(43));d.put("op_title","Image receipt");d.put("op_image","https://example.com/image.jpg");
  assertTrue(push.showNotification(new RemoteMessage.Builder("test").setData(d).build()));
  assertTrue(prefs.contains("event:"+d.get("op_receipt")+":displayed"));
  assertFalse(prefs.contains("event:"+d.get("op_receipt")+":clicked"));
 }
 @Test public void receiptQueueIsOptInAndDeduplicatesPersistedEvents(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences(ReceiptQueue.PREFS,0).edit().clear().commit();Map<String,String>d=new HashMap<>();d.put("op_receipt",new String(new char[43]).replace('\0','a'));ReceiptQueue.enqueue(c,d,"clicked",false);assertTrue(c.getSharedPreferences(ReceiptQueue.PREFS,0).getAll().isEmpty());ReceiptQueue.enqueue(c,d,"clicked",true);ReceiptQueue.enqueue(c,d,"clicked",true);assertEquals(1,c.getSharedPreferences(ReceiptQueue.PREFS,0).getAll().size());}
 @Test public void rejectsUntrustedEndpoint(){Context c=RuntimeEnvironment.getApplication();try{ReceiptQueue.configure(c,"http://external.example/events");fail();}catch(IllegalArgumentException expected){} }
 @Test public void dataMessageRecordsReceiptAndPost(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences(ReceiptQueue.PREFS,0).edit().clear().commit();OpenPush p=OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info).telemetryEndpoint("https://example.com/api/events").build();Map<String,String>d=new HashMap<>();d.put("op_receipt",new String(new char[43]).replace('\0','b'));d.put("op_v","1");d.put("op_id","receipt-test");d.put("op_type","app");d.put("op_target","main");d.put("op_title","Title");d.put("op_body","Body");assertTrue(p.showNotification(new RemoteMessage.Builder("test").setData(d).build()));assertTrue(c.getSharedPreferences(ReceiptQueue.PREFS,0).contains("event:"+d.get("op_receipt")+":received"));assertTrue(c.getSharedPreferences(ReceiptQueue.PREFS,0).contains("event:"+d.get("op_receipt")+":displayed"));}

 @Test public void disabledChannelReportsPermissionOutcomeInsteadOfDeliveryFailure(){
  Context c=RuntimeEnvironment.getApplication(); c.getSharedPreferences(ReceiptQueue.PREFS,0).edit().clear().commit();
  android.app.NotificationManager manager=(android.app.NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  manager.deleteNotificationChannel(OpenPush.CHANNEL_ID);
  manager.createNotificationChannel(new android.app.NotificationChannel(OpenPush.CHANNEL_ID,"Disabled",android.app.NotificationManager.IMPORTANCE_NONE));
  OpenPush p=OpenPush.builder(c,Activity.class,android.R.drawable.ic_dialog_info).telemetryEndpoint("https://example.com/api/events").build();
  Map<String,String>d=new HashMap<>();d.put("op_receipt","c".repeat(43));d.put("op_v","1");d.put("op_id","permission-test");d.put("op_type","app");d.put("op_target","main");
  p.showNotification(new RemoteMessage.Builder("test").setData(d).build());
  assertTrue(c.getSharedPreferences(ReceiptQueue.PREFS,0).contains("event:"+d.get("op_receipt")+":notification_disabled"));
  assertFalse(c.getSharedPreferences(ReceiptQueue.PREFS,0).contains("event:"+d.get("op_receipt")+":display_failed"));
 }

 @Test public void sessionsDeduplicateByUtcDayAndKeepLatestPermissionWithoutRawFcmToken() throws Exception {
  Context c=RuntimeEnvironment.getApplication();android.content.SharedPreferences prefs=c.getSharedPreferences(ReceiptQueue.PREFS,0);prefs.edit().clear().commit();
  long first=java.time.Instant.parse("2026-10-19T12:00:00Z").toEpochMilli();String hash="d".repeat(64);
  ReceiptQueue.enqueueSession(c,hash,first,true);ReceiptQueue.enqueueSession(c,hash,first+1,true);
  String key="event:session:"+hash+":2026-10-19";
  assertEquals(first,new org.json.JSONObject(prefs.getString(key,"{}")).getLong("occurredAt"));
  ReceiptQueue.enqueueSession(c,hash,first+2,false);
  assertFalse(new org.json.JSONObject(prefs.getString(key,"{}")).getBoolean("notificationsEnabled"));
  ReceiptQueue.enqueueSession(c,hash,first+86400000,true);
  assertTrue(prefs.contains("event:session:"+hash+":2026-10-20"));
  assertFalse(prefs.getString(key,"").contains("registrationToken"));
 }
}
