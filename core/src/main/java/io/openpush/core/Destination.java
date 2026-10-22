package io.openpush.core;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Classifies only URL-action targets. Never interprets arbitrary text as an Intent. */
public final class Destination {
    public final String url;
    public final boolean googlePlay;
    private Destination(String url, boolean googlePlay) { this.url = url; this.googlePlay = googlePlay; }

    public static boolean isPackageName(String value) {
        if (value == null || value.length() > 255 || !value.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")) return false;
        for (String part : value.split("\\.")) {
            if (("|abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|").contains("|" + part + "|")) return false;
        }
        return true;
    }

    public static Destination parse(String input) {
        if (input == null || input.length() > 2048) return null;
        if (isPackageName(input)) return store(input);
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme();
            boolean https = "https".equalsIgnoreCase(scheme), http = "http".equalsIgnoreCase(scheme);
            if ((!https && !http) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || input.indexOf('\\') >= 0 || (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80))) return null;
            if ("play.google.com".equalsIgnoreCase(uri.getHost()) && "/store/apps/details".equals(uri.getPath())) {
                if (!"/store/apps/details".equals(uri.getRawPath()) || uri.getRawFragment() != null || uri.getRawQuery() == null) return null;
                String id = null;
                for (String pair : uri.getRawQuery().split("&", -1)) {
                    String[] parts = pair.split("=", 2);
                    String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name());
                    if ("id".equals(key)) {
                        if (id != null || parts.length != 2) return null;
                        id = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
                    }
                }
                return isPackageName(id) ? store(id) : null;
            }
            return new Destination(uri.toASCIIString(), false);
        } catch (Exception invalid) { return null; }
    }

    private static Destination store(String packageName) {
        return new Destination("https://play.google.com/store/apps/details?id=" + packageName, true);
    }
}
