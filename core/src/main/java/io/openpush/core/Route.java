package io.openpush.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Validated, immutable routing envelope. Empty parse means unsupported or malformed. */
public final class Route {
    public final String id;
    public final String type;
    public final String target;
    public final Map<String, String> arguments;

    private Route(String id, String type, String target, Map<String, String> arguments) {
        this.id = id;
        this.type = type;
        this.target = target;
        this.arguments = Collections.unmodifiableMap(arguments);
    }

    public static Route parse(Map<String, String> data) {
        if (data == null || !"1".equals(data.get("op_v"))) return null;
        String id = data.get("op_id"), type = data.get("op_type"), target = data.get("op_target");
        if (id == null || !id.matches("[A-Za-z0-9._:-]{1,128}")
                || target == null || target.isEmpty() || target.length() > 2048) return null;
        if ("screen".equals(type)) {
            if (!target.matches("[A-Za-z0-9._-]{1,128}")) return null;
        } else if ("url".equals(type)) {
            Destination destination = Destination.parse(target);
            if (destination == null) return null;
            target = destination.url;
        } else if ("deeplink".equals(type)) {
            try {
                URI uri = new URI(target);
                String scheme = uri.getScheme();
                if (scheme == null || uri.getHost() == null || uri.getRawUserInfo() != null) return null;
                if ("http".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)
                        || "intent".equalsIgnoreCase(scheme) || "javascript".equalsIgnoreCase(scheme)
                        || "content".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)) return null;
            } catch (URISyntaxException e) { return null; }
        } else if (type == null || !type.matches("[a-z][a-z0-9_]{0,63}")) return null;
        Map<String, String> args = new HashMap<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            if (key != null && key.startsWith("op_arg_") && key.length() > 7) {
                if (key.length() > 128 || value == null || value.length() > 2048 || args.size() >= 32) return null;
                args.put(key.substring(7), value);
            }
        }
        return new Route(id, type, target, args);
    }
}
