package io.openpush.sample;
import android.app.Application;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class AutomaticManifestTest {
 @Test public void newInitWithoutXmlUsesFirebaseAndDefaultIconAndIsIdempotent()throws Exception{
  Application app=RuntimeEnvironment.getApplication();
  app.getApplicationInfo().flags|=android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE;
  for(com.google.firebase.FirebaseApp existing:com.google.firebase.FirebaseApp.getApps(app))existing.delete();
  com.google.firebase.FirebaseApp.initializeApp(app,new com.google.firebase.FirebaseOptions.Builder()
   .setApplicationId("1:123:android:fixture").setProjectId("fixture-project").setApiKey("AIza"+"A".repeat(35)).build());
  assertEquals(0,app.getResources().getIdentifier("openpush_project_id","string",app.getPackageName()));
  assertEquals(0,app.getResources().getIdentifier("openpush_client_key","string",app.getPackageName()));
  android.app.Activity host=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).get();
  String id="11111111-1111-4111-8111-111111111111";
  io.openpush.sdk.OpenPush.init(host,id);io.openpush.sdk.OpenPush.init(host,id);
  assertEquals(id,app.getSharedPreferences("openpush.setup.v2",0).getString("appId",""));
  com.google.firebase.messaging.RemoteMessage message=new com.google.firebase.messaging.RemoteMessage.Builder("fixture")
   .addData("op_v","1").addData("op_id","sample-new-init-01").addData("op_type","url")
   .addData("op_target","https://www.google.com").addData("op_title","Fixture").build();
  assertTrue(io.openpush.sdk.OpenPush.receive(app,message));
  android.app.NotificationManager manager=(android.app.NotificationManager)app.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
  assertEquals(1,org.robolectric.Shadows.shadowOf(manager).getAllNotifications().size());
  assertEquals(io.openpush.sdk.R.drawable.ic_stat_onesignal_default,org.robolectric.Shadows.shadowOf(manager).getAllNotifications().get(0).getSmallIcon().getResId());
 }
 @Test public void mergedManifestUsesSdkReceiverWithoutHostService(){
  Application app=RuntimeEnvironment.getApplication();int sdk=0,host=0;
  for(ResolveInfo service:app.getPackageManager().queryIntentServices(new Intent("com.google.firebase.MESSAGING_EVENT").setPackage(app.getPackageName()),0)){
   if("io.openpush.sdk.OpenPushMessagingService".equals(service.serviceInfo.name)){sdk++;assertFalse(service.serviceInfo.exported);assertEquals(-100,service.priority);}
   if(service.serviceInfo.name.startsWith("io.openpush.sample."))host++;
  }
  assertEquals(1,sdk);assertEquals(0,host);
 }
 @Test public void initializerIsPrivateAndLauncherNeedsNoCustomApplication()throws Exception{
  Application app=RuntimeEnvironment.getApplication();
  android.content.pm.ProviderInfo provider=app.getPackageManager().getProviderInfo(new ComponentName(app,"io.openpush.sdk.OpenPushInitProvider"),0);
  assertFalse(provider.exported);assertEquals(app.getPackageName()+".openpush-init",provider.authority);
  assertEquals(SimpleActivity.class.getName(),app.getPackageManager().getLaunchIntentForPackage(app.getPackageName()).getComponent().getClassName());
 }
}
