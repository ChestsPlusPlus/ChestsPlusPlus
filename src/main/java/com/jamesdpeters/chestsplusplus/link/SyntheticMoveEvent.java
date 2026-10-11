package com.jamesdpeters.chestsplusplus.link;

import lombok.Getter;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A hopper-style move fired only to ask protection plugins whether we may move items between two containers, as lock plugins guard
 * containers by cancelling {@link InventoryMoveItemEvent}. Nothing has moved yet, so our own move listeners must ignore it, unless they
 * only judge the move, as a hopper filter does.
 */
public final class SyntheticMoveEvent extends InventoryMoveItemEvent {

    /** A real move asked again with other containers: a listener that judges the real move itself can skip this one. */
    @Getter private final boolean relayed;

    private SyntheticMoveEvent(Inventory source, ItemStack item, Inventory destination, boolean relayed) {
        super(source, item, destination, false);
        this.relayed = relayed;
    }

    /** Whether other plugins let {@code item} move from {@code source} to {@code destination}. Moves nothing either way. */
    public static boolean allows(Inventory source, ItemStack item, Inventory destination) {
        return new SyntheticMoveEvent(source, item.clone(), destination, false).callEvent();
    }

    /** {@link #allows} for a real move that is under way, asked again with containers lock plugins can recognise. */
    public static boolean allowsRelayed(Inventory source, ItemStack item, Inventory destination) {
        return new SyntheticMoveEvent(source, item.clone(), destination, true).callEvent();
    }
}
