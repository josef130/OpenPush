# Realtime Database device activity

OpenPush 0.4.3 can publish the latest foreground activity and Android profile using the existing central Firebase identity.

1. Create a Spark Realtime Database in the central OpenPush Firebase project. Review and merge the openpushActivity rules from dashboard/firebase/activity-database.rules.json into the database rules. Keep Anonymous Authentication enabled for the existing central registration flow.
2. In the host Android app, add a string resource named openpush_activity_database_url with the database HTTPS origin copied from Firebase Console. Leave openpush_server_url unset when using central registration.
3. Set OPENPUSH_ACTIVITY_DATABASE_URL in the dashboard server environment to the same origin. Enable central Firestore registration as before. Run the updated dashboard server. It reads indexed activity pages and stores the data and cursor in SQLite.
4. Test on mobile data with the dashboard stopped. Reopen the dashboard and check the device row after synchronization. The SDK limits activity uploads with unchanged device metadata to one per 15 minutes. A later opening is needed to report an opening that occurred within that interval.

When moving the dashboard, use SQLite's online backup or stop the server and checkpoint its WAL before copying openpush.sqlite. Transfer token.key with the database and restore the same Firebase configuration and activity database URL. If the independent send ledger is enabled, follow dashboard/docs/SEND-LEDGER.ar.md for its separate backup and recovery procedure.

The activity record contains the foreground timestamp, model, time zone and language. A direct database write does not provide the dashboard with the phone's IP address.
