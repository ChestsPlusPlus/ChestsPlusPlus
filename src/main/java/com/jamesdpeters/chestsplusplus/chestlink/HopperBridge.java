package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.Node;
import java.util.Map;
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

/**
 * Hoppers ↔ ChestLinks via {@link HopperInventorySearchEvent} (plan §5.4, spike S1): when a hopper looks up a linked
 * block as source or destination, the group's inventory is substituted, so transfers are vanilla. This fires every
 * tick per idle hopper, so the handler is one hash lookup with no allocation.
 */
public final class HopperBridge implements Listener {

    private final Services services;

    public HopperBridge(Services services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    void onSearch(HopperInventorySearchEvent event) {
        Block block = event.getSearchBlock();
        Node node = services.nodes()
                .get(block.getWorld().getUID(), BlockPos.packed(block.getX(), block.getY(), block.getZ()));
        if (node == null || !(services.groups().byId(node.groupId()) instanceof ChestLinkGroup group)) return;
        event.setInventory(group.inventory());
        services.persistence().markHopperTouched(group);
    }

    /**
     * Droppers and crafters don't fire the search event; redirect what they push into a linked block's physical
     * container into the group instead (plan §5.4).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDropperPush(InventoryMoveItemEvent event) {
        InventoryHolder source = Holders.of(event.getSource());
        if (!(source instanceof Dropper) && !(source instanceof Crafter)) return;
        Location destination = event.getDestination().getLocation();
        if (destination == null || destination.getWorld() == null) return;
        Node node = services.nodes()
                .get(
                        destination.getWorld().getUID(),
                        BlockPos.packed(destination.getBlockX(), destination.getBlockY(), destination.getBlockZ()));
        if (node == null || !(services.groups().byId(node.groupId()) instanceof ChestLinkGroup group)) return;
        event.setCancelled(true);
        ItemStack moving = event.getItem();
        Map<Integer, ItemStack> overflow = group.inventory().addItem(moving.clone());
        int notMoved = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = moving.getAmount() - notMoved;
        if (moved <= 0) return;
        event.getSource().removeItem(moving.asQuantity(moved));
        services.persistence().markDirty(group);
    }
}
