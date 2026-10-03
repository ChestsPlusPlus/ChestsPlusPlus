package com.jamesdpeters.chestsplusplus.model;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Linked blocks by packed position (O(1) for hot paths), by chunk (display spawning, validation) and by group. Main
 * thread only.
 */
public final class NodeIndex {

    private final Map<UUID, Map<Long, Node>> byPos = new HashMap<>();
    private final Map<UUID, Map<Long, List<Node>>> byChunk = new HashMap<>();
    private final Map<Long, Map<BlockPos, Node>> byGroup = new HashMap<>();

    /** Adds or replaces the node at its position; returns the node it replaced, if any. */
    public @Nullable Node put(Node node) {
        Node previous = remove(node.pos());
        BlockPos pos = node.pos();
        byPos.computeIfAbsent(pos.world(), k -> new HashMap<>()).put(pos.packed(), node);
        byChunk.computeIfAbsent(pos.world(), k -> new HashMap<>())
                .computeIfAbsent(pos.chunkKey(), k -> new ArrayList<>(2))
                .add(node);
        byGroup.computeIfAbsent(node.groupId(), k -> new LinkedHashMap<>()).put(pos, node);
        return previous;
    }

    public @Nullable Node remove(BlockPos pos) {
        Map<Long, Node> world = byPos.get(pos.world());
        Node node = world == null ? null : world.remove(pos.packed());
        if (node == null) return null;
        Map<Long, List<Node>> chunks = byChunk.get(pos.world());
        if (chunks != null) {
            List<Node> inChunk = chunks.get(pos.chunkKey());
            if (inChunk != null) {
                inChunk.remove(node);
                if (inChunk.isEmpty()) chunks.remove(pos.chunkKey());
            }
        }
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

    public @Nullable Node get(UUID world, long packed) {
        Map<Long, Node> nodes = byPos.get(world);
        return nodes == null ? null : nodes.get(packed);
    }

    public List<Node> inChunk(UUID world, long chunkKey) {
        Map<Long, List<Node>> chunks = byChunk.get(world);
        if (chunks == null) return List.of();
        List<Node> nodes = chunks.get(chunkKey);
        return nodes == null ? List.of() : List.copyOf(nodes);
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
        List<Node> out = new ArrayList<>();
        byPos.values().forEach(world -> out.addAll(world.values()));
        return out;
    }

    public int size() {
        int size = 0;
        for (Map<Long, Node> world : byPos.values()) size += world.size();
        return size;
    }
}
