package com.jamesdpeters.chestsplusplus.core;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Item checks shared by inventories that hold nulls and empty stacks interchangeably. */
public final class Items {

    private Items() {}

    /** Whether {@code item} is a real stack: neither null nor empty (air or amount 0). Called on hot paths, so it must not allocate. */
    public static boolean present(@Nullable ItemStack item) {
        return item != null && !item.isEmpty();
    }
}
