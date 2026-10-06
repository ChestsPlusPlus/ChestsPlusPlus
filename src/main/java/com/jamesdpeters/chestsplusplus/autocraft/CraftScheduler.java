package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When each AutoCraft node next tries to craft. A node that crafted tries again one interval later; a node that failed backs off
 * (doubling, capped) and watches the blocks and ChestLink groups it depends on, so a change there retries it on the next tick instead.
 * Nodes are kept in per-tick buckets, so a tick only touches the nodes due then.
 */
final class CraftScheduler {

    static final int MAX_BACKOFF_TICKS = 100;

    private static final class State {
        long dueAt;
        int failures;
        List<BlockPos> blocks = List.of();
        List<Long> groups = List.of();
    }

    private final Map<Long, List<BlockPos>> due = new HashMap<>();
    private final Map<BlockPos, State> states = new HashMap<>();
    /** Watched block → the failing nodes next to it. */
    private final Map<BlockPos, List<BlockPos>> blockWatchers = new HashMap<>();
    /** Watched ChestLink group → the failing nodes using it. */
    private final Map<Long, List<BlockPos>> groupWatchers = new HashMap<>();
    private long now;

    /** Advances one tick and returns the nodes due now. They stay tracked until reported through {@link #crafted}, {@link #failed} or {@link #forget}. */
    List<BlockPos> advance() {
        now++;
        List<BlockPos> nodes = due.remove(now);
        return nodes == null ? List.of() : nodes;
    }

    boolean tracks(BlockPos node) {
        return states.containsKey(node);
    }

    /** Try {@code node} on the next tick, forgetting any backoff: its recipe changed or it was just linked. */
    void soon(BlockPos node) {
        State state = states.computeIfAbsent(node, _ -> new State());
        unwatch(node, state);
        state.failures = 0;
        reschedule(node, state, now + 1);
    }

    void crafted(BlockPos node, int interval) {
        State state = states.computeIfAbsent(node, _ -> new State());
        unwatch(node, state);
        state.failures = 0;
        reschedule(node, state, now + interval);
    }

    /** Backs {@code node} off and watches {@code blocks} and {@code groups}, any change to which retries it on the next tick. */
    void failed(BlockPos node, int interval, List<BlockPos> blocks, List<Long> groups) {
        State state = states.computeIfAbsent(node, _ -> new State());
        unwatch(node, state);
        state.failures++;
        reschedule(node, state, now + Math.min(MAX_BACKOFF_TICKS, (long) interval << Math.min(state.failures - 1, 8)));
        state.blocks = blocks;
        state.groups = groups;
        blocks.forEach(block -> blockWatchers.computeIfAbsent(block, _ -> new ArrayList<>(1)).add(node));
        groups.forEach(group -> groupWatchers.computeIfAbsent(group, _ -> new ArrayList<>(1)).add(node));
    }

    /** Stops tracking {@code node}; the sweep picks it up again if it's still a crafter. */
    void forget(BlockPos node) {
        State state = states.remove(node);
        if (state == null) return;
        unwatch(node, state);
        removeFromBucket(node, state.dueAt);
    }

    /** True when some node is backing off, so change hooks can return before doing any work. */
    boolean anyWaiting() {
        return !blockWatchers.isEmpty() || !groupWatchers.isEmpty();
    }

    boolean isBackingOff(BlockPos node) {
        State state = states.get(node);
        return state != null && state.failures > 0;
    }

    void blockChanged(BlockPos block) {
        List<BlockPos> nodes = blockWatchers.get(block);
        if (nodes != null) List.copyOf(nodes).forEach(this::soon);
    }

    void groupChanged(long group) {
        List<BlockPos> nodes = groupWatchers.get(group);
        if (nodes != null) List.copyOf(nodes).forEach(this::soon);
    }

    private void reschedule(BlockPos node, State state, long at) {
        if (state.dueAt != 0) removeFromBucket(node, state.dueAt);
        state.dueAt = at;
        due.computeIfAbsent(at, _ -> new ArrayList<>()).add(node);
    }

    private void removeFromBucket(BlockPos node, long at) {
        List<BlockPos> bucket = due.get(at);
        if (bucket == null) return;
        bucket.remove(node);
        if (bucket.isEmpty()) due.remove(at);
    }

    private void unwatch(BlockPos node, State state) {
        state.blocks.forEach(block -> drop(blockWatchers, block, node));
        state.groups.forEach(group -> drop(groupWatchers, group, node));
        state.blocks = List.of();
        state.groups = List.of();
    }

    private static <K> void drop(Map<K, List<BlockPos>> watchers, K key, BlockPos node) {
        List<BlockPos> nodes = watchers.get(key);
        if (nodes == null) return;
        nodes.remove(node);
        if (nodes.isEmpty()) watchers.remove(key);
    }
}
