package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** The custom holder of a ChestLink's shared 54-slot inventory (Paper's recommended identification pattern). */
public final class ChestLinkHolder implements InventoryHolder {

    private final ChestLinkGroup group;
    private Inventory inventory;

    @SuppressWarnings("NullAway.Init")
    private ChestLinkHolder(ChestLinkGroup group) {
        this.group = group;
    }

    /** Creates the shared inventory for {@code group}, fills it with {@code contents} and attaches it. */
    public static ChestLinkHolder attach(ChestLinkGroup group, Component title, @Nullable ItemStack @Nullable [] contents) {
        ChestLinkHolder holder = new ChestLinkHolder(group);
        holder.inventory = Bukkit.createInventory(holder, ChestLinkGroup.SIZE, title);
        if (contents != null) {
            for (int i = 0; i < Math.min(contents.length, ChestLinkGroup.SIZE); i++) holder.inventory.setItem(i, contents[i]);
        }
        group.attachInventory(holder.inventory);
        return holder;
    }

    /**
     * Recreates the inventory with a new title (inventory titles are fixed at creation), keeping its contents. Open
     * viewers are closed first, as the old inventory is discarded.
     */
    public void retitle(Component title) {
        @Nullable ItemStack[] contents = inventory.getContents();
        java.util.List.copyOf(inventory.getViewers()).forEach(viewer -> viewer.closeInventory());
        inventory = Bukkit.createInventory(this, ChestLinkGroup.SIZE, title);
        inventory.setContents(contents);
        group.attachInventory(inventory);
    }

    public ChestLinkGroup group() {
        return group;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
