package com.jamesdpeters.chestsplusplus.ui.menu;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * An editor of ghost items: players click copies of what's on their cursor into it, but no real item ever moves in or out. The player's
 * own inventory still works so they can pick items up. {@link MenuListener} cancels anything that would move items into the editor and
 * passes clicks on its own slots to {@link #onClick}.
 */
public abstract class GhostEditor implements InventoryHolder {

    /** A click on one of the editor's slots. The event is already cancelled. */
    public abstract void onClick(Player player, int slot, @Nullable ItemStack cursor, ClickType click);

    /** A drag of {@code cursor} across the editor's {@code slots} (in slot order). The event is already cancelled; editors ignore drags by default. */
    public void onDrag(Player player, List<Integer> slots, ItemStack cursor) {}
}
