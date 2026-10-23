package io.openpush.sdk;
import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.pm.PackageManager;
/** Host libraries may remove BOOT permission. Analytics must still work without it. */
final class BackgroundJobs {
 static JobInfo pending(JobScheduler scheduler,int id){
  if(android.os.Build.VERSION.SDK_INT>=24)return scheduler.getPendingJob(id);
  for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==id)return job;
  return null;
 }
 interface Scheduler { int schedule(JobInfo job); }
 static int submit(Scheduler scheduler,JobInfo.Builder builder,boolean persistent){
  try{return scheduler.schedule(builder.setPersisted(persistent).build());}
  catch(RuntimeException refused){
   if(persistent)try{return scheduler.schedule(builder.setPersisted(false).build());}catch(RuntimeException ignored){}
   return JobScheduler.RESULT_FAILURE;
  }
 }
 static int submit(Context context,JobScheduler scheduler,JobInfo.Builder builder){
  return submit(scheduler::schedule,builder,context.checkCallingOrSelfPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)==PackageManager.PERMISSION_GRANTED);
 }
}
