package io.openpush.sdk;

import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.gms.tasks.Tasks;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Durable network retry. Device credentials are private, excluded from Android backup. */
public final class RegistrationJob extends JobService {
 static final int ID=0x4f505247;
 private volatile int generation;
 private static long lastRequestedAt=-1;
 static synchronized void schedule(Context c){
  try{
   JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
   long now=android.os.SystemClock.elapsedRealtime();
   if(scheduler==null)return;
   // A prior installation/configuration failure may have hours of scheduler backoff.
   // A foreground request refreshes it, but repeated lifecycle/init calls are throttled.
   if(BackgroundJobs.pending(scheduler,ID)!=null && lastRequestedAt>=0 && now-lastRequestedAt<60000)return;
   BackgroundJobs.submit(c,scheduler,new JobInfo.Builder(ID,new ComponentName(c,RegistrationJob.class))
     .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL));
   lastRequestedAt=now;
  }catch(RuntimeException unavailable){android.util.Log.w("OpenPushRegistration","SCHEDULING_DEFERRED");}
 }
 interface Transport{int post(JSONObject body)throws Exception;}
 static synchronized JSONObject body(Context c,String token)throws Exception{
  SharedPreferences p=c.getSharedPreferences("openpush.registration.v1",MODE_PRIVATE);
  // Detect restore onto another installation; noBackupFilesDir is never cloud-restored.
  java.io.File marker=new java.io.File(c.getNoBackupFilesDir(),"openpush-registration");
  if(!marker.exists()){
   if(!marker.createNewFile())throw new java.io.IOException();
   p.edit().clear().commit();
  }
  String id=p.getString("id",null),secret=p.getString("secret",null);
  if(id==null || secret==null){
   id=UUID.randomUUID().toString();byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
   StringBuilder encoded=new StringBuilder();for(byte value:bytes)encoded.append(String.format(java.util.Locale.ROOT,"%02x",value&255));secret=encoded.toString();
   if(!p.edit().putString("id",id).putString("secret",secret).commit())throw new java.io.IOException();
  }
  NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  boolean enabled=androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled();
  if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=manager.getNotificationChannel(OpenPush.CHANNEL_ID);enabled &= channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
  return new JSONObject().put("appId",AutoSetup.setting(c,"openpush_app_id")).put("projectId",AutoSetup.setting(c,"openpush_project_id"))
    .put("packageName",c.getPackageName()).put("clientKey",AutoSetup.setting(c,"openpush_client_key"))
    .put("installationId",id).put("installationSecret",secret).put("token",token).put("notificationsEnabled",enabled);
 }
 static boolean upload(Context c,String token,Transport transport)throws Exception{
  int status=transport.post(body(c,token));
  if(status==403)ClientSettings.invalidateKey(c);
  if(status==200){
   byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
   StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   ReceiptQueue.enqueueSession(c,hash.toString(),System.currentTimeMillis(),body(c,token).getBoolean("notificationsEnabled"));
  }
  if((c.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.d("OpenPushRegistration","http_"+status);
  return status!=200; // Retain retry on configuration/network failures; foreground retries also refresh permissions.
 }
 @Override public boolean onStartJob(JobParameters params){
  final int current=++generation;
  new Thread(()->{
   boolean retry=true;
   try{
    if(AutoSetup.get(this)!=null){
     ClientBootstrap.ensure(this);
     String token=Tasks.await(FirebaseMessaging.getInstance().getToken(),20,TimeUnit.SECONDS);
     if(current==generation)retry=upload(this,token,body->{
      HttpURLConnection connection=(HttpURLConnection)new URL(AutoSetup.base(this)+"/api/sdk/register").openConnection();
      try{
       connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
       connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("X-OpenPush-Client","dashboard");connection.setDoOutput(true);
       byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
       try(java.io.OutputStream out=connection.getOutputStream()){out.write(bytes);}return connection.getResponseCode();
      }finally{connection.disconnect();}
     });
    }
   }catch(Exception unavailable){android.util.Log.w("OpenPushRegistration","REGISTRATION_DEFERRED");}
   if(current==generation)jobFinished(params,retry);
  },"OpenPush-registration").start();return true;
 }
 @Override public boolean onStopJob(JobParameters params){++generation;return true;}
}
