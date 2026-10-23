package io.openpush.sdk;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class BackgroundJobsTest {
 private JobInfo.Builder builder(){return new JobInfo.Builder(123,new ComponentName(RuntimeEnvironment.getApplication(),RegistrationJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);}
 @Test public void missingBootPermissionDoesNotRequestPersistence(){
  List<JobInfo> jobs=new ArrayList<>();assertEquals(JobScheduler.RESULT_SUCCESS,BackgroundJobs.submit(job->{jobs.add(job);return JobScheduler.RESULT_SUCCESS;},builder(),false));
  assertEquals(1,jobs.size());assertFalse(jobs.get(0).isPersisted());
 }
 @Test public void persistenceDeniedRetriesAsOrdinaryJob(){
  List<JobInfo> jobs=new ArrayList<>();assertEquals(JobScheduler.RESULT_SUCCESS,BackgroundJobs.submit(job->{jobs.add(job);if(job.isPersisted())throw new IllegalArgumentException("missing permission");return JobScheduler.RESULT_SUCCESS;},builder(),true));
  assertEquals(2,jobs.size());assertTrue(jobs.get(0).isPersisted());assertFalse(jobs.get(1).isPersisted());
 }
 @Test public void schedulerFailureCannotCrashHost(){assertEquals(JobScheduler.RESULT_FAILURE,BackgroundJobs.submit(job->{throw new SecurityException();},builder(),true));}
}
