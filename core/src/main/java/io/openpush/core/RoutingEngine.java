package io.openpush.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable registry. Acceptance is pure; opening is deferred until a durable click claim. */
public final class RoutingEngine<H> {
    public interface Handler<H> {
        boolean accepts(Route route);
        void open(H host, Route route);
    }

    private final Map<String, Handler<H>> handlers;

    public RoutingEngine(Map<String, Handler<H>> handlers) {
        this.handlers = Collections.unmodifiableMap(new HashMap<>(handlers));
    }

    public Handler<H> resolve(Route route) {
        if (route == null) return null;
        Handler<H> handler = handlers.get(route.type);
        return handler != null && handler.accepts(route) ? handler : null;
    }
}
