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
