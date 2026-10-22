package io.openpush.sample;

import android.os.Bundle;
import com.google.firebase.messaging.RemoteMessage;
import java.util.UUID;

/** Synthetic message fixtures for LOCAL QA ONLY; constructing one never contacts/delivers through FCM. */
public final class LocalFixtures {
    private LocalFixtures() { }
    public static RemoteMessage message(String id, String type, String target, boolean notification) {
        Bundle bundle = new Bundle();
        bundle.putString("google.message_id", id);
        if (notification) {
            bundle.putString("gcm.n.e", "1");
            bundle.putString("gcm.n.title", "OpenPush QA notification");
            bundle.putString("gcm.n.body", "Local simulation; not Firebase delivery");
        }
        if (type != null) {
            bundle.putString("op_v", "1"); bundle.putString("op_id", id);
            bundle.putString("op_type", type); bundle.putString("op_target", target);
            bundle.putString("op_arg_article_id", "42"); bundle.putString("op_arg_level", "3");
            bundle.putString("op_title", "OpenPush QA data message");
            bundle.putString("op_body", "Local simulation; not Firebase delivery");
        }
        return new RemoteMessage(bundle);
    }
    public static RemoteMessage next(String type, String target) {
        return message("local-" + UUID.randomUUID(), type, target, true);
    }
}
