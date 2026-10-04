package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
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
 * Hoppers ↔ ChestLinks via {@link HopperInventorySearchEvent}: when a hopper looks up a linked
 * block as source or destination, the group's inventory is substituted, so transfers are vanilla. This fires every
 * tick per idle hopper, so the handler is one hash lookup with no allocation.
 */
@RequiredArgsConstructor
public final class HopperBridge implements Listener {

    private final Services services;

    @EventHandler(priority = EventPriority.NORMAL)
    void onSearch(HopperInventorySearchEvent event) {
        if (!(services.groupAt(event.getSearchBlock()) instanceof ChestLinkGroup group)) return;
        event.setInventory(group.inventory());
        services.persistence().markHopperTouched(group);
    }

    /**
     * Droppers and crafters don't fire the search event; redirect what they push into a linked block's physical
     * container into the group instead.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDropperPush(InventoryMoveItemEvent event) {
        InventoryHolder source = Holders.of(event.getSource());
        if (!(source instanceof Dropper) && !(source instanceof Crafter)) return;
        Location destination = event.getDestination().getLocation();
        if (destination == null || destination.getWorld() == null) return;
        if (!(services.groupAt(destination.getBlock()) instanceof ChestLinkGroup group)) return;
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
