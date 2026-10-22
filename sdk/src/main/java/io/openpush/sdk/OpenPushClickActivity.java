package io.openpush.sdk;

import android.app.Activity;
import android.os.Bundle;

/** Notification tap endpoint: no content view and no launcher UI for external routes. */
public final class OpenPushClickActivity extends Activity {
    public static final String ACTION = "io.openpush.sdk.OPEN_NOTIFICATION";
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { OpenPush.dispatchNotificationClick(this, getIntent()); }
        catch (RuntimeException malformedOrUnavailable) { /* A bad tap must not crash the host. */ }
        finally { finish(); }
    }
}
