package io.openpush.sample;

import android.app.Application;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import io.openpush.core.Route;
import io.openpush.sdk.Router;
import io.openpush.sdk.OpenPush;

public final class SampleApplication extends Application {
    public OpenPush push;
    public TestJournal journal;
    @Override public void onCreate() {
        super.onCreate();
        journal = new TestJournal(this);
        push = OpenPush.builder(this, MainActivity.class, R.drawable.ic_notification)
                .telemetryEndpoint(BuildConfig.TELEMETRY_ENDPOINT)
                .trackMonthlyActiveUsers(!BuildConfig.TELEMETRY_ENDPOINT.isEmpty())
                .httpsHost("example.com")
                .httpsHost("www.google.com")
                .httpsHost("google.com")
                .httpHost("example.com")
                .httpsHost("play.google.com")
                .openUrlsInBrowser()
                .preferGooglePlay()
                .pipelineDiagnostics(report -> {
                    journal.recordPipeline(report.stage,report.idFingerprint,report.decision);
                    if (BuildConfig.DEBUG) Log.d("OpenPushPipeline", "stage=" + report.stage + " keys=" + report.keys
                            + " kind=" + report.kind + " input=" + report.inputFingerprint
                            + " target=" + report.targetFingerprint + " id=" + report.idFingerprint
                            + " decision=" + report.decision);
                })
                .launchDiagnostics(report -> {
                    if (BuildConfig.DEBUG) Log.d("OpenPushLaunch", "action=" + report.action + " scheme=" + report.scheme
                            + " requestedPackage=" + report.requestedPackage + " resolved=" + report.resolvedComponent
                            + " outcome=" + report.outcome + " target=" + report.destinationFingerprint
                            + " browserSelector=" + report.browserSelector);
                })
                .deepLink("openpush", "article", "/read", ArticleActivity.class)
                .screen("offers", new OpenPush.RouteHandler() {
                    public boolean accepts(Route route) { return true; }
                    public void open(Activity host, Route route) { ((MainActivity) host).showOffers(route); }
                })
                .action("article", new OpenPush.RouteHandler() {
                    public boolean accepts(Route route) {
                        String id = route.arguments.get("article_id");
                        return id != null && id.matches("[0-9]{1,12}");
                    }
                    public void open(Activity host, Route route) {
                        host.startActivity(new Intent(host, ArticleActivity.class)
                                .putExtra(Router.ARGUMENT_PREFIX + "article_id", route.arguments.get("article_id")));
                    }
                })
                // Fixed event names only. Tokens, URLs, payloads and exception messages are never supplied.
                .diagnostics(event -> {
                    if (BuildConfig.DEBUG) Log.d("OpenPush", event.name());
                    switch (event) {
                        case OPENED: journal.record(TestJournal.Event.OPENED); break;
                        case DUPLICATE: journal.record(TestJournal.Event.DUPLICATE); break;
                        case REJECTED: journal.record(TestJournal.Event.REJECTED); break;
                        case HANDLER_FAILED: journal.record(TestJournal.Event.HANDLER_FAILED); break;
                        default: break;
                    }
                })
                .build();
        push.createChannel("OpenPush notifications");
    }
}
