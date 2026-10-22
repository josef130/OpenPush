package io.openpush.sample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import io.openpush.sdk.Router;

/** Declared deep-link destination. It does not redispatch the push envelope. */
public final class ArticleActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(Router.ARGUMENT_PREFIX + "article_id");
        if (id == null && getIntent().getData() != null) id = getIntent().getData().getLastPathSegment();
        TextView view = new TextView(this);
        view.setTag("openpush.article");
        view.setPadding(32, 32, 32, 32);
        if (id == null || !id.matches("[0-9]{1,12}")) {
            view.setText("Unsupported article");
        } else {
            String level = getIntent().getStringExtra(Router.ARGUMENT_PREFIX + "level");
            view.setText("Article " + id + (level == null ? "" : "\nLevel: " + level));
        }
        setContentView(view);
    }
}
