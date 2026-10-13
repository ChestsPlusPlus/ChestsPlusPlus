package com.jamesdpeters.chestsplusplus.model;

import com.jamesdpeters.chestsplusplus.core.BlockMap;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Linked blocks by position (O(1) for hot paths) and chunk (display spawning, validation), and by group. Main thread only.
 */
public final class NodeIndex {

    private final BlockMap<Node> byBlock = new BlockMap<>();
    private final Long2ObjectMap<Map<BlockPos, Node>> byGroup = new Long2ObjectOpenHashMap<>();

    /** Adds or replaces the node at its position; returns the node it replaced, if any. */
    public @Nullable Node put(Node node) {
        Node previous = remove(node.pos());
        BlockPos pos = node.pos();
        byBlock.put(pos, node);
        byGroup.computeIfAbsent(node.groupId(), k -> new LinkedHashMap<>()).put(pos, node);
        return previous;
    }

    public @Nullable Node remove(BlockPos pos) {
        Node node = byBlock.remove(pos);
        if (node == null) return null;
        Map<BlockPos, Node> group = byGroup.get(node.groupId());
        if (group != null) {
            group.remove(pos);
            if (group.isEmpty()) byGroup.remove(node.groupId());
        }
        return node;
    }

    /** Removes every node of a group and returns them. */
    public List<Node> removeGroup(long groupId) {
        List<Node> nodes = nodesOf(groupId);
        nodes.forEach(node -> remove(node.pos()));
        return nodes;
    }

    public @Nullable Node get(BlockPos pos) {
        return get(pos.world(), pos.packed());
    }

    /** The node at {@code block}, without allocating a {@link BlockPos} (hot paths). */
    public @Nullable Node at(Block block) {
        return get(block.getWorld().getUID(), BlockPos.packed(block.getX(), block.getY(), block.getZ()));
    }

    public @Nullable Node get(UUID world, long packed) {
        return byBlock.get(world, packed);
    }

    public List<Node> inChunk(UUID world, long chunkKey) {
        return byBlock.inChunk(world, chunkKey);
    }

    public List<Node> nodesOf(long groupId) {
        Map<BlockPos, Node> nodes = byGroup.get(groupId);
        return nodes == null ? List.of() : List.copyOf(nodes.values());
    }

    public int count(long groupId) {
        Map<BlockPos, Node> nodes = byGroup.get(groupId);
        return nodes == null ? 0 : nodes.size();
    }

    public Collection<Node> all() {
        return byBlock.values();
    }

    public int size() {
        return byBlock.size();
    }
}
