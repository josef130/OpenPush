package io.openpush.sample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import io.openpush.core.Route;

public final class MainActivity extends Activity {
    private TextView destination;
    private TextView result;
    private TextView journal;
    void showOffers(Route route) { destination.setText("Offers\nCoupon: " + route.arguments.get("coupon")); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        destination = new TextView(this);
        destination.setTag("openpush.destination");
        destination.setText(state == null ? "OpenPush ready" : state.getString("destination", "OpenPush ready"));
        layout.addView(destination);
        result = new TextView(this);
        result.setTag("openpush.result");
        layout.addView(result);
        TextView mode = new TextView(this);
        boolean configured = !BuildConfig.LOCAL_ONLY_BUILD && !FirebaseApp.getApps(this).isEmpty();
        mode.setText(configured ? "Firebase configured. Console delivery still requires a real send."
                : "LOCAL QA ONLY. Firebase is not configured; Console delivery is untested.");
        layout.addView(mode);
        Button permission = new Button(this);
        permission.setText("Enable notifications");
        permission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        });
        layout.addView(permission);
        TextView token = new TextView(this);
        token.setTextIsSelectable(true);
        token.setText("Loading FCM test token…");
        layout.addView(token);
        if (configured) FirebaseMessaging.getInstance().getToken().addOnCompleteListener(this, task ->
                token.setText(task.isSuccessful() ? task.getResult() : "Token unavailable; check Firebase/network/Play services"));
        else token.setText("No FCM token in local-only mode. Add real Firebase config and rebuild normally.");
        addLocal(layout, "LOCAL: normal notification", null, null);
        addLocal(layout, "LOCAL: HTTPS A", "url", "https://example.com/?openpush=qa-A");
        addLocal(layout, "LOCAL: HTTPS B", "url", "https://example.com/?openpush=qa-B");
        addLocal(layout, "LOCAL: internal article 42", "deeplink", "openpush://article/read/42");
        addLocal(layout, "LOCAL: invalid URL", "url", "javascript:alert(1)");
        addLocal(layout, "LOCAL: unsupported action", "unsupported", "missing");
        Button replay = new Button(this);
        replay.setText("LOCAL: replay current click Intent");
        replay.setOnClickListener(v -> handle(getIntent()));
        layout.addView(replay);
        journal = new TextView(this);
        journal.setTag("openpush.journal");
        layout.addView(journal);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("destination", destination.getText().toString());
        super.onSaveInstanceState(out);
    }

    private void handle(Intent intent) {
        io.openpush.sdk.OpenPush.ClickResult status = ((SampleApplication) getApplication()).push.handleIntent(this, intent);
        result.setText("Click result: " + status.name());
        refreshJournal();
    }
    private void addLocal(LinearLayout layout, String label, String type, String target) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> {
            SampleApplication app = (SampleApplication) getApplication();
            app.journal.record(TestJournal.Event.LOCAL_SIMULATION);
            HostMessageHandler.receive(app, LocalFixtures.next(type, target));
            refreshJournal();
        });
        layout.addView(button);
    }
    private void refreshJournal() {
        if (journal != null) journal.setText(((SampleApplication) getApplication()).journal.summary());
    }
    @Override protected void onResume() {
        super.onResume();
        refreshJournal();
    }
}
