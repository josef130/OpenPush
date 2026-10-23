package io.openpush.sdk;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={21,22,23},manifest=Config.NONE)
public class LegacyAndroidTest {
    @Test public void registrationAndRetryDoNotRequireNewerFrameworkApis() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        assertTrue(RegistrationJob.body(context,"test-token").has("notificationsEnabled"));
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo.Builder builder=new JobInfo.Builder(702,new ComponentName(context,RegistrationJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        assertEquals(JobScheduler.RESULT_SUCCESS,BackgroundJobs.submit(context,scheduler,builder));
        assertNotNull(BackgroundJobs.pending(scheduler,702));
        assertNull(BackgroundJobs.pending(scheduler,703));
    }
    @Test public void optionalImageCannotCrashOlderPhones() {
        NotificationImageJob.schedule(RuntimeEnvironment.getApplication(),"legacy","https://example.com/image.jpg");
    }
}
