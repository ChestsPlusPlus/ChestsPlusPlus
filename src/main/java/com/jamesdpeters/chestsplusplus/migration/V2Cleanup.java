package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every block an import has linked. A repeated import skips them, so a block a player has since unlinked isn't linked again. Those whose
 * world cleanup (facing, v2 sign, armour stands, double chest) waits for their chunk to load are pending. Main thread only.
 */
public final class V2Cleanup {

    /** A chunk with blocks waiting. */
    public record ChunkRef(UUID world, long chunkKey) {

        public int x() {
            return (int) chunkKey;
        }

        public int z() {
            return (int) (chunkKey >> 32);
        }
    }

    private final Set<BlockPos> imported = new HashSet<>();
    private final Map<ChunkRef, Set<BlockPos>> pending = new HashMap<>();
    private Consumer<BlockPos> onChange = pos -> {};
    private int size;

    /** Called with each position whose state changed (persistence marks it dirty). */
    public void onChange(Consumer<BlockPos> listener) {
        this.onChange = listener;
    }

    /** Records a newly imported block, waiting for cleanup. */
    public void add(BlockPos pos) {
        boolean added = imported.add(pos);
        if (addPending(pos) || added) onChange.accept(pos);
    }

    /** The block's cleanup is done (or no longer needed); it stays recorded as imported. */
    public void remove(BlockPos pos) {
        ChunkRef chunk = chunk(pos);
        Set<BlockPos> set = pending.get(chunk);
        if (set == null || !set.remove(pos)) return;
        if (set.isEmpty()) pending.remove(chunk);
        size--;
        onChange.accept(pos);
    }

    public boolean wasImported(BlockPos pos) {
        return imported.contains(pos);
    }

    public boolean contains(BlockPos pos) {
        Set<BlockPos> set = pending.get(chunk(pos));
        return set != null && set.contains(pos);
    }

    public List<BlockPos> inChunk(UUID world, long chunkKey) {
        Set<BlockPos> set = pending.get(new ChunkRef(world, chunkKey));
        return set == null ? List.of() : List.copyOf(set);
    }

    public List<ChunkRef> chunks() {
        return new ArrayList<>(pending.keySet());
    }

    /** True when no block is waiting for cleanup. */
    public boolean isEmpty() {
        return size == 0;
    }

    /** How many blocks are waiting for cleanup. */
    public int size() {
        return size;
    }

    /** Replaces all state (used on load). Does not fire change notifications. */
    public void load(Collection<BlockPos> importedBlocks, Collection<BlockPos> pendingBlocks) {
        imported.clear();
        pending.clear();
        size = 0;
        imported.addAll(importedBlocks);
        pendingBlocks.forEach(this::addPending);
    }

    private boolean addPending(BlockPos pos) {
        if (!pending.computeIfAbsent(chunk(pos), k -> new LinkedHashSet<>()).add(pos)) return false;
        size++;
        return true;
    }

    private static ChunkRef chunk(BlockPos pos) {
        return new ChunkRef(pos.world(), pos.chunkKey());
    }
}
