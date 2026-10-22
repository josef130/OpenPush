package io.openpush.core;

import java.util.HashMap;
import java.util.Map;

final class InfrastructureTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Infrastructure check " + checks);
    }
    private static final class Store implements ClickLedger.Store {
        String value = "";
        boolean fail;
        public String read() { return value; }
        public boolean save(String ids) { if (fail) return false; value = ids; return true; }
    }
    static int run() {
        Store store = new Store();
        ClickLedger ledger = new ClickLedger(store, 2);
        check(ledger.claim("a") == ClickLedger.Claim.NEW);
        check(ledger.claim("a") == ClickLedger.Claim.DUPLICATE);
        check(new ClickLedger(store, 2).claim("a") == ClickLedger.Claim.DUPLICATE);
        check(ledger.claim("b") == ClickLedger.Claim.NEW);
        check(ledger.claim("c") == ClickLedger.Claim.NEW);
        check(ledger.claim("b") == ClickLedger.Claim.DUPLICATE);
        check(ledger.claim("a") == ClickLedger.Claim.NEW);
        store.fail = true;
        check(ledger.claim("d") == ClickLedger.Claim.STORAGE_FAILED);
        store.fail = false;
        check(ledger.claim("d") == ClickLedger.Claim.NEW);
        boolean invalid = false;
        try { ledger.claim("a\nb"); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid);

        Map<String, String> payload = new HashMap<>();
        payload.put("op_v", "1"); payload.put("op_id", "test"); payload.put("op_type", "custom");
        payload.put("op_target", "article"); payload.put("op_arg_id", "42");
        Route route = Route.parse(payload);
        check(route != null && "42".equals(route.arguments.get("id")));
        Map<String, RoutingEngine.Handler<Object>> handlers = new HashMap<>();
        RoutingEngine.Handler<Object> handler = new RoutingEngine.Handler<Object>() {
            public boolean accepts(Route r) { return "article".equals(r.target); }
            public void open(Object host, Route r) { }
        };
        handlers.put("custom", handler);
        RoutingEngine<Object> engine = new RoutingEngine<>(handlers);
        handlers.clear();
        check(engine.resolve(route) == handler);
        check(engine.resolve(null) == null);
        payload.put("op_type", "unknown"); check(engine.resolve(Route.parse(payload)) == null);
        payload.put("op_type", "custom"); payload.put("op_target", "unsupported");
        check(engine.resolve(Route.parse(payload)) == null);
        payload.put("op_type", "deeplink"); payload.put("op_target", "openpush://article/42");
        check(Route.parse(payload) != null);
        payload.put("op_target", "intent://article/42"); check(Route.parse(payload) == null);
        payload.put("op_target", "openpush://user@article/42"); check(Route.parse(payload) == null);
        payload.put("op_target", "https://example.com/article/42"); check(Route.parse(payload) != null);
        payload.put("op_type", "INVALID ACTION"); check(Route.parse(payload) == null);
        return checks;
    }
}
