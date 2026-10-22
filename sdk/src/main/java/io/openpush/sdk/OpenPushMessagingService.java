package io.openpush.sdk;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
/** Low-priority fallback. An existing host service must delegate using OpenPush.receive(). */
public final class OpenPushMessagingService extends FirebaseMessagingService {
 @Override public void onMessageReceived(RemoteMessage message){OpenPush.receive(this,message);}
 @Override public void onNewToken(String token){AutoSetup.register(this);}
}
