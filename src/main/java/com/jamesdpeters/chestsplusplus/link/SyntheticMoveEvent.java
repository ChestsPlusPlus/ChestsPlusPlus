package com.jamesdpeters.chestsplusplus.link;

import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A hopper-style move fired only to ask protection plugins whether we may move items between two containers, as lock plugins guard
 * containers by cancelling {@link InventoryMoveItemEvent}. Nothing has moved yet, so our own move listeners must ignore it.
 */
public final class SyntheticMoveEvent extends InventoryMoveItemEvent {

    private SyntheticMoveEvent(Inventory source, ItemStack item, Inventory destination) {
        super(source, item, destination, false);
    }

    /** Whether other plugins let {@code item} move from {@code source} to {@code destination}. Moves nothing either way. */
    public static boolean allows(Inventory source, ItemStack item, Inventory destination) {
        SyntheticMoveEvent event = new SyntheticMoveEvent(source, item.clone(), destination);
        return event.callEvent();
    }
}
