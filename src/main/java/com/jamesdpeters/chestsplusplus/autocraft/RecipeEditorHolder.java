package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.ui.menu.GhostEditor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The ghost-item recipe editor: a crafting-table UI where slot 0 shows the result and slots 1-9 the
 * matrix. Clicking a matrix slot with an item places a ghost copy (nothing is consumed); clicking with an empty
 * cursor clears it.
 */
public final class RecipeEditorHolder extends GhostEditor {

    public static final int RESULT_SLOT = 0;

    @Getter private final AutoCraftGroup group;
    private final AutoCraftService autoCraft;
    private final Inventory inventory;

    @SuppressWarnings("this-escape") // a custom holder must pass itself to createInventory
    public RecipeEditorHolder(AutoCraftGroup group, Component title, AutoCraftService autoCraft) {
        this.group = group;
        this.autoCraft = autoCraft;
        this.inventory = Bukkit.createInventory(this, InventoryType.WORKBENCH, title);
        render();
    }

    /** The matrix after clicking {@code slot} with {@code cursor}, or null if the click changes nothing. */
    public @Nullable ItemStack @Nullable [] click(int slot, @Nullable ItemStack cursor) {
        if (slot < 1 || slot > 9) return null;
        @Nullable ItemStack[] matrix = group.matrix();
        boolean hasCursor = cursor != null && !cursor.isEmpty();
        ItemStack current = matrix[slot - 1];
        if (!hasCursor && current == null) return null;
        matrix[slot - 1] = hasCursor ? cursor.asOne() : null;
        return matrix;
    }

    @Override
    public void onClick(Player player, int slot, @Nullable ItemStack cursor, ClickType click) {
        autoCraft.edit(player, this, slot, cursor);
    }

    public void render() {
        @Nullable ItemStack[] matrix = group.matrix();
        for (int i = 0; i < 9; i++) inventory.setItem(i + 1, matrix[i]);
        inventory.setItem(RESULT_SLOT, group.result());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
