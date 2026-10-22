package io.openpush.sdk;

import io.openpush.core.Destination;
import io.openpush.core.Route;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Opt-in metadata only. Fingerprints are salted per process, never payload values. */
public final class PipelineReport {
    private static final String SALT = UUID.randomUUID().toString();
    public final String stage, kind, inputFingerprint, targetFingerprint, idFingerprint, decision;
    public final Set<String> keys;

    PipelineReport(String stage, Map<String, String> data, Set<String> receivedKeys,
                   Route route, String decision) {
        this.stage = stage;
        TreeSet<String> names = new TreeSet<>();
        for (String key : receivedKeys) {
            if (names.size() == 64) break;
            // Do not allow arbitrary key text, control characters, or huge names into logs.
            names.add(key != null && key.matches("[A-Za-z0-9_.-]{1,64}") ? key : "<redacted-key>");
        }
        keys = Collections.unmodifiableSet(names);
        String input = data.get("op_target");
        Destination destination = "url".equals(data.get("op_type")) ? Destination.parse(input) : null;
        kind = input == null ? "NONE" : destination != null
                ? (destination.googlePlay ? (Destination.isPackageName(input) ? "PACKAGE_TO_PLAY" : "PLAY") : "WEB")
                : route != null ? "OTHER_ROUTE" : "INVALID";
        inputFingerprint = fingerprint(input);
        targetFingerprint = fingerprint(route == null ? null : route.target);
        idFingerprint = fingerprint(data.get("op_id"));
        this.decision = decision;
    }

    static String fingerprint(String value) {
        if (value == null) return "NONE";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((SALT + "\u0000" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
