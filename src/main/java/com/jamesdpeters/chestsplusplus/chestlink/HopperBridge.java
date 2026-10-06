package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.block.Dropper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Hoppers ↔ ChestLinks via {@link HopperInventorySearchEvent}: when a hopper looks up a linked
 * block as source or destination, the group's inventory is substituted, so transfers are vanilla. This fires every
 * tick per idle hopper, so the handler is one hash lookup with no allocation.
 */
@RequiredArgsConstructor
public final class HopperBridge implements Listener {

    private final Plugin plugin;
    private final Services services;
    private final ChestLinkService chestLinks;
    /** Linked blocks a crafter pushed into this tick, absorbed together on the next. */
    private final Set<BlockPos> pendingAbsorbs = new HashSet<>();

    @EventHandler(priority = EventPriority.NORMAL)
    void onSearch(HopperInventorySearchEvent event) {
        if (!(services.groupAt(event.getSearchBlock()) instanceof ChestLinkGroup group)) return;
        event.setInventory(group.inventory());
        services.groupStore().markDirty(group);
    }

    /** Droppers and crafters don't fire the search event, so what they push into a linked block is redirected here. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBlockPush(InventoryMoveItemEvent event) {
        InventoryHolder source = Holders.of(event.getSource());
        if (!(source instanceof Dropper) && !(source instanceof Crafter)) return;
        Block destination = destinationBlock(event);
        if (destination == null || !(services.groupAt(destination) instanceof ChestLinkGroup group)) return;
        if (source instanceof Crafter) absorbNextTick(destination);
        else pushFromDropper(event, group);
    }

    private static @Nullable Block destinationBlock(InventoryMoveItemEvent event) {
        Location location = event.getDestination().getLocation();
        return location == null || location.getWorld() == null ? null : location.getBlock();
    }

    /** A dropper keeps its item when the move is cancelled, so it can be moved by hand; a full group leaves it in the dropper. */
    private void pushFromDropper(InventoryMoveItemEvent event, ChestLinkGroup group) {
        event.setCancelled(true);
        ItemStack moving = event.getItem();
        Map<Integer, ItemStack> overflow = group.inventory().addItem(moving.clone());
        int notMoved = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = moving.getAmount() - notMoved;
        if (moved <= 0) return;
        event.getSource().removeItem(moving.asQuantity(moved));
        services.groupStore().markDirty(group);
    }

    /**
     * A crafter ejects its result into the world when the move is cancelled, and its event item is the new result rather than a grid
     * slot, so the move goes ahead into the empty physical container and is absorbed into the group on the next tick.
     */
    private void absorbNextTick(Block block) {
        if (pendingAbsorbs.add(BlockPos.of(block)) && pendingAbsorbs.size() == 1) {
            plugin.getServer().getScheduler().runTask(plugin, this::absorbPending);
        }
    }

    private void absorbPending() {
        List<BlockPos> positions = List.copyOf(pendingAbsorbs);
        pendingAbsorbs.clear();
        for (BlockPos pos : positions) {
            Block block = pos.isLoaded() ? pos.block() : null;
            if (block != null && services.groupAt(block) instanceof ChestLinkGroup group) chestLinks.absorbPhysicalContents(group, block);
        }
    }
}
