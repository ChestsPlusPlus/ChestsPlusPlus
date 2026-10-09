package com.jamesdpeters.chestsplusplus.link;

import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * World changes to linked blocks, for both group types: breaking (incl. Silk Touch), explosions, pistons,
 * burning, entity block changes, double-chest prevention, link-item placement and chunk load/unload. All lookups are
 * index lookups.
 */
@RequiredArgsConstructor
public final class NodeListener implements Listener {

    private final Services services;
    private final LinkService links;
    private final DisplayService displays;
    private final LinkItem linkItems;

    private boolean isLinked(Block block) {
        return services.nodes().at(block) != null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Node node = services.nodes().at(block);
        if (node == null) return;
        Player player = event.getPlayer();
        StorageGroup group = services.groups().byId(node.groupId());
        Location dropAt = block.getLocation().clone().add(0.5, 0.5, 0.5);
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean silkTouch = player.getGameMode() != GameMode.CREATIVE && tool.containsEnchantment(Enchantment.SILK_TOUCH) && group != null;
        if (silkTouch) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(dropAt, linkItems.create(group, block.getType(), services.messages()));
        }
        links.unlink(node.pos(), dropAt, silkTouch);
        if (group == null) return;
        boolean removed = services.groups().byId(group.id()) == null;
        Message message = removed
                ? group.type().pick(Message.CHESTLINK_REMOVED, Message.AUTOCRAFT_REMOVED)
                : group.type().pick(Message.CHESTLINK_UNLINKED, Message.AUTOCRAFT_UNLINKED);
        services.send(player, message, Messages.group(group));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        LinkItem.Link link = linkItems.read(event.getItemInHand());
        StorageGroup group = link == null ? null : services.groups().byId(link.groupId());
        boolean stale = link != null && (group == null || group.type() != link.type());
        if (stale) services.send(event.getPlayer(), Message.ERROR_STALE_LINK_ITEM);
        else if (group != null && relink(event, group)) return;
        preventDoubleChest(event.getBlockPlaced());
    }

    /** Links a placed link item back to its group. Returns false if the block can't join the group, so it places normally. */
    private boolean relink(BlockPlaceEvent event, StorageGroup group) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Message refusal = relinkRefusal(player, group, block);
        if (refusal != null) {
            services.send(player, refusal, Messages.group(group));
            event.setCancelled(true);
            return true;
        }
        GroupTypeHandler handler = links.handler(group.type());
        if (handler == null || !handler.isValidBlock(block)) return false;
        links.join(player, group, block, Holders.facing(player).getOppositeFace(),
                group.type().pick(Message.CHESTLINK_LINKED, Message.AUTOCRAFT_LINKED));
        return true;
    }

    private @Nullable Message relinkRefusal(Player player, StorageGroup group, Block block) {
        Message refusal = links.linkingRefusal(player, group.type(), block.getWorld());
        if (refusal != null) return refusal;
        if (!services.access().canAccess(player.getUniqueId(), player, group)) return Message.ERROR_NO_ACCESS;
        return null;
    }

    /** A chest placed next to a linked chest must not merge with it. */
    private void preventDoubleChest(Block placed) {
        if (!(placed.getBlockData() instanceof Chest data) || data.getType() == Chest.Type.SINGLE) return;
        Block partner = placed.getRelative(DoubleChests.partnerDirection(data));
        if (isLinked(partner) || isLinked(placed)) DoubleChests.split(placed);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isLinked(block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isLinked(block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyLinked(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyLinked(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBurn(BlockBurnEvent event) {
        if (isLinked(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isLinked(event.getBlock())) event.setCancelled(true);
    }

    private boolean anyLinked(List<Block> blocks) {
        return blocks.stream().anyMatch(this::isLinked);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChunkLoad(ChunkLoadEvent event) {
        var world = event.getWorld().getUID();
        long key = event.getChunk().getChunkKey();
        List<Node> inChunk = services.nodes().inChunk(world, key);
        if (inChunk.isEmpty()) return;
        links.unlinkChangedBlocks(inChunk);
        displays.chunkLoaded(world, key);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChunkUnload(ChunkUnloadEvent event) {
        displays.chunkUnloaded(event.getWorld().getUID(), event.getChunk().getChunkKey());
    }

    /** Facing for a node linked by clicking a face: the clicked face if horizontal, else towards the player. */
    public static BlockFace facingFor(@Nullable BlockFace clicked, Player player) {
        if (clicked != null && clicked.isCartesian() && clicked.getModY() == 0) return clicked;
        return Holders.facing(player).getOppositeFace();
    }
}
