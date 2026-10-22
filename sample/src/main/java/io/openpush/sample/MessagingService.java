package io.openpush.sample;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class MessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        SampleApplication app = (SampleApplication) getApplication();
        app.journal.record(TestJournal.Event.FCM_CALLBACK);
        HostMessageHandler.receive(app, message);
    }
    @Override public void onNewToken(String token) {
        // Representative existing host behavior. Never store/log the token in the QA journal.
        ((SampleApplication) getApplication()).journal.record(TestJournal.Event.TOKEN_CALLBACK);
    }
    @Override public void onDeletedMessages() {
        ((SampleApplication) getApplication()).journal.record(TestJournal.Event.DELETED_CALLBACK);
    }
}
