package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * Unresolved locations belong to the original imported group, even after it is renamed. Also indexed by chunk, so a chunk load
 * only looks at its own rows: rows for a world that never comes back would otherwise be scanned on every load. Main thread only.
 */
public final class V2PendingLocations {

    public record LocationRow(long groupId, String worldName, int x, int y, int z) {}

    private record ChunkRef(String worldName, long chunkKey) {}

    private final Map<Long, Set<LocationRow>> locations = new HashMap<>();
    private final Map<ChunkRef, Set<LocationRow>> byChunk = new HashMap<>();
    private LongConsumer onChange = id -> {};

    public void onChange(LongConsumer listener) {
        onChange = listener;
    }

    public void add(LocationRow row) {
        if (index(row)) onChange.accept(row.groupId());
    }

    public void remove(LocationRow row) {
        Set<LocationRow> rows = locations.get(row.groupId());
        if (rows == null || !rows.remove(row)) return;
        if (rows.isEmpty()) locations.remove(row.groupId());
        unindexChunk(row);
        onChange.accept(row.groupId());
    }

    public void removeGroup(long id) {
        Set<LocationRow> rows = locations.remove(id);
        if (rows == null) return;
        rows.forEach(this::unindexChunk);
        onChange.accept(id);
    }

    public List<LocationRow> forGroup(long id) {
        return List.copyOf(locations.getOrDefault(id, Set.of()));
    }

    /** A copy, so callers can remove rows while iterating. */
    public List<LocationRow> inChunk(String worldName, long chunkKey) {
        return List.copyOf(byChunk.getOrDefault(new ChunkRef(worldName, chunkKey), Set.of()));
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
        byChunk.clear();
        rows.forEach(this::index);
    }

    private boolean index(LocationRow row) {
        if (!locations.computeIfAbsent(row.groupId(), id -> new LinkedHashSet<>()).add(row)) return false;
        byChunk.computeIfAbsent(chunkOf(row), k -> new LinkedHashSet<>()).add(row);
        return true;
    }

    private void unindexChunk(LocationRow row) {
        ChunkRef chunk = chunkOf(row);
        Set<LocationRow> rows = byChunk.get(chunk);
        if (rows == null) return;
        rows.remove(row);
        if (rows.isEmpty()) byChunk.remove(chunk);
    }

    private static ChunkRef chunkOf(LocationRow row) {
        return new ChunkRef(row.worldName(), BlockPos.chunkKey(row.x() >> 4, row.z() >> 4));
    }
}
