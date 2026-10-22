package io.openpush.core;

import java.util.HashMap;
import java.util.Map;

/** Dependency-free executable tests; failures exit nonzero without relying on -ea. */
public final class RouteTest {
    private static int checks;
    private static Map<String, String> valid() {
        Map<String, String> data = new HashMap<>();
        data.put("op_v", "1"); data.put("op_id", "test-001");
        data.put("op_type", "screen"); data.put("op_target", "offers");
        return data;
    }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(Route.parse(null) == null, "null payload");
        check(Route.parse(new HashMap<>()) == null, "unrelated payload");
        Map<String, String> data = valid();
        data.put("op_arg_coupon", "WELCOME"); data.put("future_field", "ignored");
        Route route = Route.parse(data);
        check(route != null && "offers".equals(route.target), "screen route");
        check("WELCOME".equals(route.arguments.get("coupon")), "arguments");
        data.put("op_arg_coupon", "changed");
        check("WELCOME".equals(route.arguments.get("coupon")), "defensive copy");
        boolean immutable = false;
        try { route.arguments.put("x", "y"); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "immutable arguments");
        for (String key : new String[]{"op_v", "op_id", "op_type", "op_target"}) {
            data = valid(); data.remove(key); check(Route.parse(data) == null, "missing " + key);
        }
        for (String id : new String[]{"", "a\nb", "a/b", "a b", new String(new char[129]).replace('\0', 'x')}) {
            data = valid(); data.put("op_id", id); check(Route.parse(data) == null, "invalid id");
        }
        data = valid(); data.put("op_v", "2"); check(Route.parse(data) == null, "unknown version");
        data = valid(); data.put("op_type", "activity"); check(Route.parse(data) != null, "custom action envelope");
        data = valid(); data.put("op_target", "a/b"); check(Route.parse(data) == null, "invalid screen");
        for (String url : new String[]{"javascript:alert(1)", "file:///tmp/a",
                "intent://a", "https://user@example.com", "https://example.com:8443/a", "https:///a", "https://example.com/a b"}) {
            data = valid(); data.put("op_type", "url"); data.put("op_target", url);
            check(Route.parse(data) == null, "unsafe/malformed URL " + url);
        }
        data = valid(); data.put("op_type", "url"); data.put("op_target", "https://example.com:443/offers?q=1");
        check(Route.parse(data) != null, "HTTPS URL");
        for (String url : new String[]{"http://example.com/read?q=1", "https://example.com/", "http://example.com:80/"}) {
            data = valid(); data.put("op_type", "url"); data.put("op_target", url);
            check(Route.parse(data) != null, "web classification");
        }
        for (String input : new String[]{"com.chikh.nano", "com.example_app.Game2", "org.example",
                "https://play.google.com/store/apps/details?id=com.chikh.nano",
                "https://play.google.com/store/apps/details?%69d=com.chikh.nano&referrer=ignored"}) {
            Destination destination = Destination.parse(input);
            check(destination != null && destination.googlePlay, "valid package/store destination");
        }
        check("https://play.google.com/store/apps/details?id=com.chikh.nano".equals(Destination.parse("com.chikh.nano").url), "package canonicalization");
        for (String input : new String[]{"random text", "nano", "com..nano", ".com.nano", "com.nano.", "com.2nano",
                "com.class.nano", "com.foo/bar", "com.chikh.nano\n", "https://play.google.com/store/apps/details",
                "https://play.google.com/store/apps/details?id=", "https://play.google.com/store/apps/details?id=com.foo&id=com.bar",
                "https://play.google.com/store/apps/details?id=com.foo&%69d=com.bar",
                "https://play.google.com/store/apps/details?id=com.foo%2Fbar", "https://play.google.com/store/apps/details?id=%2563om.foo",
                "https://play.google.com/store/apps/details?id=com.foo#id=com.bar", "https://user@play.google.com/store/apps/details?id=com.foo",
                "https://play.google.com/store/apps/%64etails?id=com.foo", "http://example.com:8080/"}) {
            check(Destination.parse(input) == null, "reject invalid destination");
        }
        data = valid(); data.put("op_arg_x", new String(new char[2049]).replace('\0', 'x'));
        check(Route.parse(data) == null, "oversized argument");
        data = valid(); for (int i = 0; i < 33; i++) data.put("op_arg_" + i, "x");
        check(Route.parse(data) == null, "argument count bound");
        checks += InfrastructureTest.run();
        System.out.println("PASS: " + checks + " core contract checks");
    }
}
