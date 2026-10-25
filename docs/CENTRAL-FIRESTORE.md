# Central Firebase transport — unpublished development work

The SDK can use OpenPush's central Firebase project for installation enrollment
and receipt uploads while retaining the host app's Firebase project for FCM.
The activity API remains `OpenPush.init(this, "APP_ID")`. No custom Application,
manual FirebaseMessagingService or openpush.xml is required for a host without
an existing messaging service. An existing host service must still delegate
OpenPush messages and token changes according to the integration guide.

`central-firebase.properties` contains public Firebase client options compiled
into this SDK. It must never contain an Admin SDK private key or Service Account.
A named secondary FirebaseApp and isolated anonymous identity do not change the
host's default FirebaseApp or authenticated user. The host still supplies its
own google-services.json and notification permission.

Phones submit requests to central Firestore over HTTPS. The local dashboard
verifies the registered package/project, sends an expiring FCM challenge to the
host token, and approves membership only after the same phone identity returns
the proof. Requests can wait in Firestore while the dashboard is offline.
Sending campaigns and approving new devices still require the local dashboard.

Display receipts and clicks upload independently using durable jobs. Firestore
rules prevent reading another phone's records or creating arbitrary receipts;
the dashboard reserves a receipt before sending. Existing queued local HTTP
receipts retain their original endpoint and do not block central receipt uploads.
Explicit legacy `openpush_server_url` resources retain the previous transport.

This development feature is not yet a published release. Physical registration,
display-before-click and routing must pass before release. App Check enforcement,
quota/abuse controls, retention and app revocation remain deployment work.
Do not treat Spark as unlimited or this transport as a workaround for Android
force-stop, disabled notifications or manufacturer background restrictions.
