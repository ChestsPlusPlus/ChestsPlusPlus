package com.jamesdpeters.chestsplusplus.core;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/** Finding and closing the players looking at an inventory. */
public final class Viewers {

    private Viewers() {}

    public static void close(Inventory inventory) {
        close(inventory, viewer -> true);
    }

    /** Closes the views of {@code inventory} that {@code which} accepts, and returns those viewers. */
    public static List<HumanEntity> close(Inventory inventory, Predicate<HumanEntity> which) {
        // Closing a view removes it from getViewers(), so walk a copy.
        List<HumanEntity> closing = inventory.getViewers().stream().filter(which).toList();
        closing.forEach(HumanEntity::closeInventory);
        return closing;
    }

    /** Online players whose open top inventory belongs to a {@code type} holder. */
    public static <H> List<Player> withHolder(Class<H> type) {
        return withHolder(type, holder -> true);
    }

    /** Online players whose open top inventory belongs to a {@code type} holder that {@code which} accepts. */
    public static <H> List<Player> withHolder(Class<H> type, Predicate<H> which) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> {
                    InventoryHolder holder = topHolder(player);
                    return type.isInstance(holder) && which.test(type.cast(holder));
                })
                .map(Player.class::cast)
                .toList();
    }

    /** The holder of the player's open top inventory, without a block-state snapshot. */
    public static @Nullable InventoryHolder topHolder(HumanEntity player) {
        // The top inventory can be null for players with nothing open on some platforms (MockBukkit).
        @Nullable Inventory top = player.getOpenInventory().getTopInventory();
        return top == null ? null : Holders.of(top);
    }
}
