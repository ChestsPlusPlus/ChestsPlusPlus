package com.jamesdpeters.chestsplusplus.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Values by block position, per world, with each chunk's positions indexed as well. {@link #get(UUID, long)} is two hash lookups and
 * allocates nothing, so hot paths can use it. Main thread only.
 */
public final class BlockMap<V> {

    private static final class InWorld<V> {
        final Long2ObjectMap<V> byPos = new Long2ObjectOpenHashMap<>();
        /** Insertion-ordered, so a chunk's values come back in the order they were added. */
        final Long2ObjectMap<LongSet> byChunk = new Long2ObjectOpenHashMap<>();
    }

    private final Map<UUID, InWorld<V>> worlds = new HashMap<>();

    /** The value at a {@link BlockPos#packed()} position, without allocating (hot paths). */
    public @Nullable V get(UUID world, long packed) {
        InWorld<V> in = worlds.get(world);
        return in == null ? null : in.byPos.get(packed);
    }

    public @Nullable V get(BlockPos pos) {
        return get(pos.world(), pos.packed());
    }

    /** Returns the value it replaced, if any. */
    public @Nullable V put(BlockPos pos, V value) {
        InWorld<V> in = worlds.computeIfAbsent(pos.world(), k -> new InWorld<>());
        V previous = in.byPos.put(pos.packed(), value);
        if (previous == null) in.byChunk.computeIfAbsent(pos.chunkKey(), k -> new LongLinkedOpenHashSet(2)).add(pos.packed());
        return previous;
    }

    public @Nullable V remove(BlockPos pos) {
        InWorld<V> in = worlds.get(pos.world());
        if (in == null) return null;
        V removed = in.byPos.remove(pos.packed());
        if (removed == null) return null;
        LongSet chunk = in.byChunk.get(pos.chunkKey());
        if (chunk != null) {
            chunk.remove(pos.packed());
            if (chunk.isEmpty()) in.byChunk.remove(pos.chunkKey());
        }
        return removed;
    }

    /** The values in one chunk ({@link BlockPos#chunkKey()}), in the order they were added. */
    public List<V> inChunk(UUID world, long chunkKey) {
        InWorld<V> in = worlds.get(world);
        LongSet chunk = in == null ? null : in.byChunk.get(chunkKey);
        if (in == null || chunk == null) return List.of();
        List<V> values = new ArrayList<>(chunk.size());
        for (LongIterator it = chunk.iterator(); it.hasNext();) values.add(in.byPos.get(it.nextLong()));
        return values;
    }

    /** Removes everything in one chunk and returns where it was. */
    public List<BlockPos> removeChunk(UUID world, long chunkKey) {
        InWorld<V> in = worlds.get(world);
        LongSet chunk = in == null ? null : in.byChunk.remove(chunkKey);
        if (in == null || chunk == null) return List.of();
        List<BlockPos> removed = new ArrayList<>(chunk.size());
        for (LongIterator it = chunk.iterator(); it.hasNext();) {
            long packed = it.nextLong();
            in.byPos.remove(packed);
            removed.add(BlockPos.unpack(world, packed));
        }
        return removed;
    }

    public void forEach(BiConsumer<BlockPos, V> action) {
        worlds.forEach((world, in) -> in.byPos.long2ObjectEntrySet()
                .forEach(entry -> action.accept(BlockPos.unpack(world, entry.getLongKey()), entry.getValue())));
    }

    public List<V> values() {
        List<V> values = new ArrayList<>(size());
        worlds.values().forEach(in -> values.addAll(in.byPos.values()));
        return values;
    }

    public int size() {
        return worlds.values().stream().mapToInt(in -> in.byPos.size()).sum();
    }

    public void clear() {
        worlds.clear();
    }
}
