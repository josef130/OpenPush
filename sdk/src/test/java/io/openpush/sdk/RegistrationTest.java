package io.openpush.sdk;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class RegistrationTest {
 @Test public void tokenRotationKeepsInstallationAndPrivateSecret()throws Exception{
  Context c=RuntimeEnvironment.getApplication();
  JSONObject first=RegistrationJob.body(c,"first-token");JSONObject second=RegistrationJob.body(c,"rotated-token");
  assertEquals(first.getString("installationId"),second.getString("installationId"));
  assertEquals(first.getString("installationSecret"),second.getString("installationSecret"));
  assertEquals("rotated-token",second.getString("token"));
  assertEquals(64,second.getString("installationSecret").length());
 }
 @Test public void offlineAndServerFailureRetriedButSuccessCompletes()throws Exception{
  Context c=RuntimeEnvironment.getApplication();
  assertTrue(RegistrationJob.upload(c,"token",body->503));
  assertFalse(RegistrationJob.upload(c,"token",body->200));
 }
 @Test public void explicitBuilderHostsRemainOptInWithoutAutoConfig(){assertNull(AutoSetup.get(RuntimeEnvironment.getApplication()));}
}
