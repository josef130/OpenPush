package io.openpush.sdk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import io.openpush.core.Route;
import io.openpush.core.Destination;
import io.openpush.core.RoutingEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Central immutable routing rules. Remote payloads cannot nominate arbitrary components. */
public final class Router {
    public static final String ARGUMENT_PREFIX = "io.openpush.arg.";
    private final RoutingEngine<Activity> engine;

    private Router(Map<String, RoutingEngine.Handler<Activity>> handlers) {
        engine = new RoutingEngine<>(handlers);
    }

    RoutingEngine.Handler<Activity> resolve(Route route) { return engine.resolve(route); }

    public static final class Builder {
        private final Context context;
        private final Map<String, OpenPush.RouteHandler> screens = new HashMap<>();
        private final Map<String, RoutingEngine.Handler<Activity>> actions = new HashMap<>();
        private final Set<String> hosts = new HashSet<>();
        private final List<DeepLinkRule> links = new ArrayList<>();
        private boolean preferGooglePlay;
        private boolean browserOnly;
        private final Set<String> httpHosts = new HashSet<>();
        private LaunchDiagnostics launchDiagnostics = report -> { };

        public Builder(Context context) { this.context = context.getApplicationContext(); }

        public Builder screen(String name, OpenPush.RouteHandler handler) {
            if (name == null || !name.matches("[A-Za-z0-9._-]{1,128}") || handler == null || screens.containsKey(name)) {
                throw new IllegalArgumentException("Invalid or duplicate screen rule");
            }
            screens.put(name, handler);
            return this;
        }

        public Builder httpsHost(String host) {
            if (host == null || !host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")) {
                throw new IllegalArgumentException("An exact HTTPS hostname is required");
            }
            hosts.add(host.toLowerCase(Locale.ROOT));
            return this;
        }

        /** Prefer the official Store for approved play.google.com URLs; does not approve the host itself. */
        public Builder preferGooglePlay() { preferGooglePlay = true; return this; }
        /** Keeps the legacy browser-category Store fallback; ordinary web URLs use Android's default resolution. */
        public Builder openUrlsInBrowser() { browserOnly = true; return this; }
        /** HTTP remains opt-in separately from existing HTTPS approvals. */
        public Builder httpHost(String host) {
            Builder validation = new Builder(context);
            validation.httpsHost(host);
            httpHosts.add(host.toLowerCase(Locale.ROOT));
            return this;
        }
        public Builder launchDiagnostics(LaunchDiagnostics listener) {
            if (listener == null) throw new IllegalArgumentException("Listener required");
            launchDiagnostics = listener; return this;
        }

        /** Path matches exactly or at a slash boundary; component must have a matching manifest filter. */
        public Builder deepLink(String scheme, String host, String path, Class<? extends Activity> activity) {
            if (scheme == null || !scheme.matches("[a-z][a-z0-9+.-]*") || host == null
                    || !host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")
                    || path == null || !path.startsWith("/") || activity == null) {
                throw new IllegalArgumentException("Invalid deep link rule");
            }
            if ("http".equals(scheme) || "intent".equals(scheme) || "file".equals(scheme)
                    || "content".equals(scheme) || "javascript".equals(scheme) || "data".equals(scheme)) {
                throw new IllegalArgumentException("Unsafe deep link scheme");
            }
            links.add(new DeepLinkRule(scheme, host, path, activity.getName()));
            return this;
        }

        public Builder action(String type, OpenPush.RouteHandler handler) {
            if (type == null || !type.matches("[a-z][a-z0-9_]{0,63}") || handler == null
                    || "screen".equals(type) || "url".equals(type) || "deeplink".equals(type)
                    || actions.containsKey(type)) throw new IllegalArgumentException("Invalid or duplicate action");
            actions.put(type, handler);
            return this;
        }

        public Router build() {
            Map<String, RoutingEngine.Handler<Activity>> all = new HashMap<>(actions);
            Map<String, OpenPush.RouteHandler> screenRules = new HashMap<>(screens);
            Set<String> allowedHosts = new HashSet<>(hosts);
            boolean nativePlay = preferGooglePlay;
            boolean browserUrls = browserOnly;
            Set<String> approvedHttp = new HashSet<>(httpHosts);
            LaunchDiagnostics launches = launchDiagnostics;
            List<DeepLinkRule> deepRules = new ArrayList<>(links);
            all.putIfAbsent("app", new OpenPush.RouteHandler() {
                public boolean accepts(Route route) { return "main".equals(route.target); }
                public void open(Activity host, Route route) { /* Host launcher is already visible. */ }
            });
            all.put("screen", new OpenPush.RouteHandler() {
                public boolean accepts(Route route) {
                    OpenPush.RouteHandler handler = screenRules.get(route.target);
                    return handler != null && handler.accepts(route);
                }
                public void open(Activity host, Route route) { screenRules.get(route.target).open(host, route); }
            });
            all.put("url", new OpenPush.RouteHandler() {
                public boolean accepts(Route route) {
                    String host = Uri.parse(route.target).getHost();
                    Set<String> approved = "http".equalsIgnoreCase(Uri.parse(route.target).getScheme()) ? approvedHttp : allowedHosts;
                    return host != null && approved.contains(host.toLowerCase(Locale.ROOT));
                }
                public void open(Activity host, Route route) {
                    Destination classified = Destination.parse(route.target);
                    Intent destination = view(route);
                    if (classified != null && classified.googlePlay) destination.addCategory(Intent.CATEGORY_BROWSABLE);
                    if (nativePlay && classified != null && classified.googlePlay) {
                        try {
                            // Build the Store request independently: no browser selector/category
                            // and no launch intent for the package advertised in the notification.
                            launch(host, view(route).setPackage("com.android.vending"), context, launches);
                            return;
                        } catch (RuntimeException unavailable) {
                            report(launches, destination, null, "STORE_FALLBACK");
                        }
                    }
                    if (browserUrls && classified != null && classified.googlePlay) {
                        destination.setSelector(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER));
                    }
                    launch(host, destination, context, launches);
                }
            });
            all.put("deeplink", new OpenPush.RouteHandler() {
                private ComponentName component(Route route) {
                    Uri uri = Uri.parse(route.target);
                    String path = uri.getPath() == null ? "" : uri.getPath();
                    // Encoded separators/dot segments can be interpreted differently by host routers.
                    for (String segment : path.split("/")) if (".".equals(segment) || "..".equals(segment)) return null;
                    String raw = uri.getEncodedPath() == null ? "" : uri.getEncodedPath().toLowerCase(Locale.ROOT);
                    if (raw.contains("%2f") || raw.contains("%5c") || path.contains("\\") || path.contains("%")) return null;
                    Intent candidate = view(route).setPackage(context.getPackageName()).addCategory(Intent.CATEGORY_BROWSABLE);
                    ComponentName selected = null;
                    for (DeepLinkRule rule : deepRules) {
                        if (!rule.scheme.equalsIgnoreCase(uri.getScheme()) || !rule.host.equalsIgnoreCase(uri.getHost())
                                || !("/".equals(rule.path) || path.equals(rule.path)
                                || path.startsWith(rule.path.endsWith("/") ? rule.path : rule.path + "/"))) continue;
                        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(candidate, PackageManager.MATCH_DEFAULT_ONLY)) {
                            if (info.activityInfo != null && context.getPackageName().equals(info.activityInfo.packageName)
                                    && rule.activity.equals(info.activityInfo.name)) {
                                ComponentName match = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                                if (selected != null && !selected.equals(match)) return null;
                                selected = match;
                            }
                        }
                    }
                    return selected;
                }
                public boolean accepts(Route route) { return component(route) != null; }
                public void open(Activity host, Route route) {
                    ComponentName destination = component(route);
                    if (destination == null) throw new IllegalStateException("Deep link destination unavailable");
                    host.startActivity(view(route).addCategory(Intent.CATEGORY_BROWSABLE).setComponent(destination));
                }
            });
            return new Router(all);
        }
    }

    public interface LaunchDiagnostics { void onLaunch(LaunchReport report); }
    /** No URI path/query, package-to-install, message ID, extras or exception message. */
    public static final class LaunchReport {
        public final String action, scheme, requestedPackage, resolvedComponent, outcome;
        public final String destinationFingerprint;
        public final boolean browserSelector;
        LaunchReport(Intent intent, String resolved, String outcome) {
            action = intent.getAction(); scheme = intent.getScheme(); requestedPackage = intent.getPackage();
            resolvedComponent = resolved; this.outcome = outcome;
            destinationFingerprint = PipelineReport.fingerprint(intent.getDataString());
            browserSelector = intent.getSelector() != null
                    && intent.getSelector().hasCategory(Intent.CATEGORY_APP_BROWSER);
        }
    }
    private static void report(LaunchDiagnostics listener, Intent intent, String resolved, String outcome) {
        try { listener.onLaunch(new LaunchReport(intent, resolved, outcome)); } catch (RuntimeException ignored) { }
    }
    private static void launch(Activity host, Intent intent, Context context, LaunchDiagnostics listener) {
        String resolved = null;
        String resolvedPackage = null;
        try {
            ResolveInfo info = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null) {
                resolvedPackage = info.activityInfo.packageName;
                resolved = new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToShortString();
            }
        } catch (RuntimeException unavailable) { /* Visibility restrictions must not block launch. */ }
        if ("com.android.vending".equals(intent.getPackage()) && resolvedPackage != null
                && !"com.android.vending".equals(resolvedPackage)) {
            report(listener, intent, resolved, "STORE_HANDLER_MISMATCH");
            // Some OEM resolvers return an intermediary even for a package-limited request.
            // Only a unique exported, enabled handler advertised by the Store itself is usable.
            // Android still enforces the component's permissions at startActivity().
            ComponentName official = verifiedStoreHandler(context, intent);
            if (official == null) throw new ActivityNotFoundException("Official Store handler unavailable");
            intent.setComponent(official);
            resolved = official.flattenToShortString();
            report(listener, intent, resolved, "VERIFIED_STORE_HANDLER");
        }
        report(listener, intent, resolved, "REQUESTED");
        try {
            host.startActivity(intent);
            // Android returning normally does not prove that an external UI became visible.
            report(listener, intent, resolved, "RETURNED");
        } catch (RuntimeException failure) {
            report(listener, intent, resolved, failure instanceof ActivityNotFoundException ? "NOT_FOUND"
                    : failure instanceof SecurityException ? "DENIED" : "FAILED");
            throw failure;
        }
    }

    private static ComponentName verifiedStoreHandler(Context context, Intent intent) {
        ComponentName selected = null;
        try {
            for (ResolveInfo match : context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                android.content.pm.ActivityInfo info = match.activityInfo;
                if (info == null || !"com.android.vending".equals(info.packageName) || info.name == null
                        || !info.exported || !info.enabled || info.applicationInfo == null || !info.applicationInfo.enabled
                        || (info.permission != null && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)) continue;
                ComponentName candidate = new ComponentName(info.packageName, info.name);
                if (selected != null && !selected.equals(candidate)) return null;
                selected = candidate;
            }
        } catch (RuntimeException unavailable) { return null; }
        return selected;
    }

    private static Intent view(Route route) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(route.target));
        for (Map.Entry<String, String> arg : route.arguments.entrySet()) {
            intent.putExtra(ARGUMENT_PREFIX + arg.getKey(), arg.getValue());
        }
        return intent;
    }

    private static final class DeepLinkRule {
        final String scheme, host, path, activity;
        DeepLinkRule(String scheme, String host, String path, String activity) {
            this.scheme = scheme; this.host = host; this.path = path; this.activity = activity;
        }
    }
}
