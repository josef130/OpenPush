package io.openpush.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.openpush.core.Route;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Device tests: intentionally separate from JVM parser checks. */
@RunWith(AndroidJUnit4.class)
public final class ClickTest {
    private Context context;
    private OpenPush push;
    private int opens;
    private final OpenPush.RouteHandler allow = new OpenPush.RouteHandler() {
        public boolean accepts(Route route) { return "offers".equals(route.target); }
        public void open(Activity host, Route route) { opens++; }
    };

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getSharedPreferences("openpush.clicks.v1", Context.MODE_PRIVATE).edit().clear().commit());
        push = new OpenPush(context, Activity.class, android.R.drawable.ic_dialog_info);
        opens = 0;
    }

    private Intent click(String id) {
        return new Intent().putExtra("op_v", "1").putExtra("op_id", id)
                .putExtra("op_type", "screen").putExtra("op_target", "offers");
    }

    @Test public void repeatedEntryPointsAndNewAdapterDoNotDispatchAgain() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(null, click("a"), allow));
            assertEquals(OpenPush.ClickResult.DUPLICATE, push.handleIntent(null, click("a"), allow));
            OpenPush recreated = new OpenPush(context, Activity.class, android.R.drawable.ic_dialog_info);
            assertEquals(OpenPush.ClickResult.DUPLICATE, recreated.handleIntent(null, click("a"), allow));
            assertEquals(OpenPush.ClickResult.OPENED, recreated.handleIntent(null, click("b"), allow));
            assertEquals(2, opens);
        });
    }

    @Test public void rejectedRouteDoesNotConsumeId() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertEquals(OpenPush.ClickResult.REJECTED,
                    push.handleIntent(null, click("a").putExtra("op_target", "unknown"), allow));
            assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(null, click("a"), allow));
            assertEquals(1, opens);
        });
    }

    @Test public void malformedAndUnrelatedExtrasNeverDispatch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertEquals(OpenPush.ClickResult.IGNORED, push.handleIntent(null, new Intent(), allow));
            assertEquals(OpenPush.ClickResult.REJECTED,
                    push.handleIntent(null, click("a").putExtra("op_id", 5), allow));
            assertEquals(OpenPush.ClickResult.REJECTED,
                    push.handleIntent(null, click("a").putExtra("op_v", "2"), allow));
            assertEquals(0, opens);
        });
    }

    @Test public void thrownCallbackRemainsClaimed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            OpenPush.RouteHandler broken = new OpenPush.RouteHandler() {
                public boolean accepts(Route route) { return true; }
                public void open(Activity host, Route route) { throw new IllegalStateException("test"); }
            };
            assertEquals(OpenPush.ClickResult.HANDLER_FAILED, push.handleIntent(null, click("a"), broken));
            assertEquals(OpenPush.ClickResult.DUPLICATE, push.handleIntent(null, click("a"), allow));
            assertEquals(0, opens);
        });
    }

    @Test public void boundedLedgerEvictsOldest() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < 129; i++) {
                assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(null, click("id-" + i), allow));
            }
            assertEquals(OpenPush.ClickResult.DUPLICATE, push.handleIntent(null, click("id-128"), allow));
            assertEquals(OpenPush.ClickResult.OPENED, push.handleIntent(null, click("id-0"), allow));
            assertEquals(130, opens);
        });
    }
}
