package io.openpush.sample;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import io.openpush.sdk.OpenPush;
/** No custom Application, host messaging service, click forwarding, or token upload. */
public final class SimpleActivity extends Activity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  OpenPush.init(this,BuildConfig.AUTOMATIC_APP_ID);
  TextView text=new TextView(this);text.setText("OpenPush automatic integration\nNo host FirebaseMessagingService\nNo custom Application");text.setPadding(32,64,32,32);setContentView(text);
 }
}
