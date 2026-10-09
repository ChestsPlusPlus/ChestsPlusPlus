package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocations.LocationRow;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/** Attaches deferred locations to the current group state, without reading the YAML again or loading chunks. */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class V2LocationRecovery implements Listener {

    private final Server server;
    private final V2PendingLocations pending;
    private final GroupRegistry groups;
    private final NodeIndex nodes;
    private final GroupStore groupStore;
    private final LinkService links;
    private final DisplayService displays;
    private final V2Cleanup cleanup;
    private final V2WorldCleanup worldCleanup;

    public void finishLoaded() {
        server.getWorlds().forEach(this::finishWorld);
    }

    @EventHandler
    void onWorldLoad(WorldLoadEvent event) {
        finishWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onChunkLoad(ChunkLoadEvent event) {
        finish(event.getChunk());
    }

    private void finishWorld(World world) {
        for (Chunk chunk : world.getLoadedChunks()) finish(chunk);
    }

    public void finish(Chunk chunk) {
        if (pending.isEmpty()) return;
        for (LocationRow row : pending.inChunk(chunk.getWorld().getName(), chunk.getChunkKey())) attach(row, chunk);
    }

    private void attach(LocationRow row, Chunk chunk) {
        StorageGroup group = groups.byId(row.groupId());
        if (group == null) {
            pending.remove(row);
            return;
        }
        World world = chunk.getWorld();
        BlockPos pos;
        try {
            pos = new BlockPos(world.getUID(), row.x(), row.y(), row.z());
        } catch (IllegalArgumentException e) {
            discard(row, "position outside the world");
            return;
        }
        if (row.y() < world.getMinHeight() || row.y() >= world.getMaxHeight()) {
            discard(row, "height outside the world");
            return;
        }
        Block block = chunk.getBlock(row.x() & 15, row.y(), row.z() & 15);
        var handler = links.handler(group.type());
        if (nodes.get(pos) != null || cleanup.wasImported(pos) || handler == null || !handler.isValidBlock(block)) {
            discard(row, "block is stale or already claimed");
            return;
        }
        Node node = new Node(pos, BlockFace.NORTH, group.id());
        nodes.put(node);
        groupStore.markDirty(group);
        cleanup.add(pos);
        pending.remove(row);
        worldCleanup.finish(chunk);
        displays.nodeAdded(nodes.get(pos));
        log.info("Recovered v2 location {} for group {}", pos, group.id());
    }

    private void discard(LocationRow row, String reason) {
        pending.remove(row);
        log.warn("Discarded pending v2 location {}: {}", row, reason);
    }
}
