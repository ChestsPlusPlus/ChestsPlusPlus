package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.DoubleChests;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.jspecify.annotations.Nullable;

/**
 * Finishes imported blocks once their chunk is loaded: the facing comes from the v2 sign (which is removed, as v3 sign linking does), a
 * double chest is split, and v2's armour-stand displays are removed. Costs one hash lookup per chunk load once nothing is waiting.
 */
@RequiredArgsConstructor
public final class V2WorldCleanup implements Listener {

    private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    private static final List<String> SIGN_TAGS = List.of("[chestlink]", "[autocraft]");

    private final V2Cleanup cleanup;
    private final NodeIndex nodes;
    private final GroupRegistry groups;
    private final GroupStore groupStore;
    private final DisplayService displays;
    /** v2 tagged its armour stands with this key; the plugin name, and so the namespace, is unchanged. */
    private final NamespacedKey v2StandKey;

    /** Runs before the node listener queues the chunk's displays, so they spawn facing the right way. */
    @EventHandler(priority = EventPriority.LOWEST)
    void onChunkLoad(ChunkLoadEvent event) {
        if (!cleanup.isEmpty()) finishBlocks(event.getChunk());
    }

    /** Entities load after their chunk in Paper, so the stands go here; a stand can sit in the chunk next to its block's. */
    @EventHandler
    void onEntitiesLoad(EntitiesLoadEvent event) {
        if (cleanup.isEmpty()) return;
        removeStands(event.getEntities());
        finishChunk(event.getChunk());
    }

    /** Finishes every waiting chunk that is already loaded (after an import, and at startup). */
    public void finishLoaded() {
        for (V2Cleanup.ChunkRef ref : cleanup.chunks()) {
            List<BlockPos> waiting = cleanup.inChunk(ref.world(), ref.chunkKey());
            if (waiting.isEmpty() || !waiting.getFirst().isLoaded()) continue;
            Block block = waiting.getFirst().block();
            if (block != null) finish(block.getChunk());
        }
    }

    /** Finishes a loaded chunk's blocks; once its entities are in too, removes v2's stands and stops waiting on it. */
    public void finish(Chunk chunk) {
        finishBlocks(chunk);
        if (!chunk.isEntitiesLoaded()) return;
        removeStands(List.of(chunk.getEntities()));
        finishChunk(chunk);
    }

    private void finishChunk(Chunk chunk) {
        finishBlocks(chunk);
        cleanup.inChunk(chunk.getWorld().getUID(), chunk.getChunkKey()).forEach(cleanup::remove);
    }

    /** Idempotent: a second pass finds no sign and the same facing. */
    private void finishBlocks(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        for (BlockPos pos : cleanup.inChunk(world, chunk.getChunkKey())) {
            Node node = nodes.get(pos);
            if (node == null) {
                cleanup.remove(pos);
                continue;
            }
            Block block = chunk.getBlock(pos.x() & 15, pos.y(), pos.z() & 15);
            BlockFace signFace = removeV2Sign(block);
            DoubleChests.split(block);
            BlockFace facing = LinkService.front(block, signFace != null ? signFace : node.facing());
            if (facing != node.facing()) turn(node, facing);
        }
    }

    private void turn(Node node, BlockFace facing) {
        Node turned = new Node(node.pos(), facing, node.groupId());
        displays.nodeRemoved(node);
        nodes.put(turned);
        displays.nodeAdded(turned);
        StorageGroup group = groups.byId(node.groupId());
        if (group != null) groupStore.markDirty(group);
    }

    /** Removes the v2 link sign hanging on {@code block}, returning the face it was on (v2 put it on the block's front). */
    private static @Nullable BlockFace removeV2Sign(Block block) {
        for (BlockFace side : SIDES) {
            Block signBlock = block.getRelative(side);
            if (!(signBlock.getBlockData() instanceof WallSign wallSign) || wallSign.getFacing() != side) continue;
            if (!(signBlock.getState(false) instanceof Sign sign) || !isV2Sign(sign)) continue;
            signBlock.setType(Material.AIR, false);
            return side;
        }
        return null;
    }

    private static boolean isV2Sign(Sign sign) {
        String text = sign.getSide(Side.FRONT).lines().stream().map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", String::concat).toLowerCase(Locale.ROOT);
        return SIGN_TAGS.stream().anyMatch(text::contains);
    }

    private void removeStands(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof ArmorStand stand && stand.getPersistentDataContainer().has(v2StandKey)) stand.remove();
        }
    }
}
