package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.core.BlockMap;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

/** The compiled filters of every filtered hopper in a loaded chunk. Built from hopper PDC on chunk load and on edit. */
public final class FilterIndex {

    private final BlockMap<CompiledFilter> filters = new BlockMap<>();

    /** Hot path: the compiled filter for a hopper position, or null when it has none. Two hash lookups, no allocation. */
    public @Nullable CompiledFilter get(UUID world, int x, int y, int z) {
        return filters.get(world, BlockPos.packed(x, y, z));
    }

    public @Nullable CompiledFilter get(Block block) {
        return get(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public int indexedCount() {
        return filters.size();
    }

    void put(Block hopper, CompiledFilter filter) {
        filters.put(BlockPos.of(hopper), filter);
    }

    void remove(Block hopper) {
        filters.remove(BlockPos.of(hopper));
    }

    /** Drops an unloading chunk's hoppers and returns where they were. */
    List<BlockPos> removeChunk(Chunk chunk) {
        return filters.removeChunk(chunk.getWorld().getUID(), BlockPos.chunkKey(chunk.getX(), chunk.getZ()));
    }

    void forEach(BiConsumer<BlockPos, CompiledFilter> action) {
        filters.forEach(action);
    }

    void clear() {
        filters.clear();
    }
}
