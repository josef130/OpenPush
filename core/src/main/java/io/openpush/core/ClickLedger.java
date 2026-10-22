package io.openpush.core;

import java.util.ArrayList;
import java.util.List;

/** Bounded at-most-once dispatch ledger. Synchronization across instances belongs to the adapter. */
public final class ClickLedger {
    public interface Store {
        String read();
        boolean save(String ids);
    }
    public enum Claim { NEW, DUPLICATE, STORAGE_FAILED }
    private final Store store;
    private final int capacity;

    public ClickLedger(Store store, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.store = store;
        this.capacity = capacity;
    }

    public Claim claim(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid click ID");
        }
        List<String> ids = new ArrayList<>();
        for (String saved : store.read().split("\n")) if (!saved.isEmpty()) ids.add(saved);
        if (ids.contains(id)) return Claim.DUPLICATE;
        ids.add(id);
        while (ids.size() > capacity) ids.remove(0);
        StringBuilder next = new StringBuilder();
        for (String saved : ids) next.append(saved).append('\n');
        return store.save(next.toString()) ? Claim.NEW : Claim.STORAGE_FAILED;
    }
}
