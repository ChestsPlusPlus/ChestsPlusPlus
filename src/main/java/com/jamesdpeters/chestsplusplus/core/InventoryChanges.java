package com.jamesdpeters.chestsplusplus.core;

import org.bukkit.inventory.Inventory;

/** Told when the plugin moves items itself, which no event reports, so AutoCrafters waiting on that inventory can try again. */
@FunctionalInterface
public interface InventoryChanges {

    void changed(Inventory inventory);
}
