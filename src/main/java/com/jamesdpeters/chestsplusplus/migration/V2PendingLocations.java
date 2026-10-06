package com.jamesdpeters.chestsplusplus.migration;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

/** Unresolved locations belong to the original imported group, even after it is renamed. Main thread only. */
public final class V2PendingLocations {

    public record LocationRow(long groupId, String worldName, int x, int y, int z) {}

    private final Map<Long, Set<LocationRow>> locations = new HashMap<>();
    private LongConsumer onChange = id -> {};

    public void onChange(LongConsumer listener) {
        onChange = listener;
    }

    public void add(LocationRow row) {
        if (locations.computeIfAbsent(row.groupId(), id -> new LinkedHashSet<>()).add(row)) onChange.accept(row.groupId());
    }

    public void remove(LocationRow row) {
        Set<LocationRow> rows = locations.get(row.groupId());
        if (rows == null || !rows.remove(row)) return;
        if (rows.isEmpty()) locations.remove(row.groupId());
        onChange.accept(row.groupId());
    }

    public void removeGroup(long id) {
        if (locations.remove(id) != null) onChange.accept(id);
    }

    public List<LocationRow> forGroup(long id) {
        return List.copyOf(locations.getOrDefault(id, Set.of()));
    }

    public List<LocationRow> all() {
        return locations.values().stream().flatMap(Set::stream).toList();
    }

    public boolean isEmpty() {
        return locations.isEmpty();
    }

    public int size() {
        return locations.values().stream().mapToInt(Set::size).sum();
    }

    public void load(List<LocationRow> rows) {
        locations.clear();
        rows.forEach(row -> locations.computeIfAbsent(row.groupId(), id -> new LinkedHashSet<>()).add(row));
    }
}
