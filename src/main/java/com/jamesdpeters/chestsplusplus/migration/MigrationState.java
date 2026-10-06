package com.jamesdpeters.chestsplusplus.migration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Where the hopper-filter migration stands. Main thread only. */
public final class MigrationState {

    /** v2 hopper filters (item frames on hoppers): not needed, waiting for an admin, converting as chunks load, or finished. */
    public enum Filters {
        NONE,
        PENDING,
        ON_LOAD,
        DONE
    }

    static final String FILTERS = "filters";
    static final String IMPORT = "v2_import";

    private final Map<String, String> values = new HashMap<>();
    private Consumer<String> onChange = key -> {};

    /** Called with each key that changed (persistence marks it dirty). */
    public void onChange(Consumer<String> listener) {
        this.onChange = listener;
    }

    public Filters filters() {
        try {
            return Filters.valueOf(values.getOrDefault(FILTERS, Filters.NONE.name()));
        } catch (IllegalArgumentException e) {
            return Filters.NONE;
        }
    }

    public void setFilters(Filters filters) {
        set(FILTERS, filters.name());
    }

    public boolean importCompleted() {
        return values.containsKey(IMPORT);
    }

    public String importStatus() {
        return values.getOrDefault(IMPORT, "NOT_IMPORTED");
    }

    public void completeImport() {
        set(IMPORT, "COMPLETE");
    }

    public void legacyImport() {
        set(IMPORT, "LEGACY");
    }

    @Nullable
    String get(String key) {
        return values.get(key);
    }

    private void set(String key, String value) {
        if (value.equals(values.put(key, value))) return;
        onChange.accept(key);
    }

    /** Replaces all state (used on load). Does not fire change notifications. */
    void load(Map<String, String> loaded) {
        values.clear();
        values.putAll(loaded);
    }
}
