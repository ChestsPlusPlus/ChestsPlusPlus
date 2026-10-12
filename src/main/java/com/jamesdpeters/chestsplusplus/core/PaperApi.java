package com.jamesdpeters.chestsplusplus.core;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Paper calls whose annotations NullAway misreads, wrapped once so call sites stay checked. */
public final class PaperApi {

    private PaperApi() {}

    /**
     * {@code inventory.getContents()}: empty slots are null, the array never is. Paper declares it with JetBrains'
     * {@code @Nullable ItemStack[]}, which NullAway also reads as a nullable array.
     */
    @SuppressWarnings("NullAway")
    public static @Nullable ItemStack[] contents(Inventory inventory) {
        return inventory.getContents();
    }

    /** {@code inventory.getStorageContents()}, read as {@link #contents}. */
    @SuppressWarnings("NullAway")
    public static @Nullable ItemStack[] storageContents(Inventory inventory) {
        return inventory.getStorageContents();
    }

    /**
     * Where the entity is. {@code player.getLocation()} resolves to {@code OfflinePlayer}'s {@code @Nullable} one, though an online
     * player always has a location; called through {@code Entity}, it's non-null.
     */
    public static Location location(Entity entity) {
        return entity.getLocation();
    }
}
