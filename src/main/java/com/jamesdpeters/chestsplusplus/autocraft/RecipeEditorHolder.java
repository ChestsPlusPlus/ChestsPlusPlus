package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import com.jamesdpeters.chestsplusplus.ui.menu.GhostEditor;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.List;
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
 * The ghost-item recipe editor: a crafting-table UI where slot 0 shows the result and slots 1-9 the matrix. Clicking a matrix slot with
 * an item places a ghost copy (nothing is consumed); clicking with an empty cursor clears it, and right-clicking changes what it matches.
 */
public final class RecipeEditorHolder extends GhostEditor {

    public static final int RESULT_SLOT = 0;

    @Getter private final AutoCraftGroup group;
    private final AutoCraftService autoCraft;
    private final Messages messages;
    private final Inventory inventory;

    @SuppressWarnings("this-escape") // a custom holder must pass itself to createInventory
    public RecipeEditorHolder(AutoCraftGroup group, Component title, AutoCraftService autoCraft, Messages messages) {
        this.group = group;
        this.autoCraft = autoCraft;
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, InventoryType.WORKBENCH, title);
        render();
    }

    /** The matrix index of an editor slot, or -1 for the result slot. */
    public static int matrixIndex(int slot) {
        return slot >= 1 && slot <= 9 ? slot - 1 : -1;
    }

    /** The matrix after clicking {@code slot} with {@code cursor}, or null if the click changes nothing. */
    public @Nullable ItemStack @Nullable [] click(int slot, @Nullable ItemStack cursor) {
        int index = matrixIndex(slot);
        if (index < 0) return null;
        @Nullable ItemStack[] matrix = group.matrix();
        boolean hasCursor = cursor != null && !cursor.isEmpty();
        if (!hasCursor && matrix[index] == null) return null;
        matrix[index] = hasCursor ? cursor.asOne() : null;
        return matrix;
    }

    /** The matrix after placing a ghost of {@code cursor} in every matrix slot of {@code slots}, or null if none of them is in the matrix. */
    public @Nullable ItemStack @Nullable [] place(List<Integer> slots, ItemStack cursor) {
        @Nullable ItemStack[] matrix = group.matrix();
        boolean placed = false;
        for (int slot : slots) {
            int index = matrixIndex(slot);
            if (index < 0) continue;
            matrix[index] = cursor.asOne();
            placed = true;
        }
        return placed ? matrix : null;
    }

    @Override
    public void onClick(Player player, int slot, @Nullable ItemStack cursor, ClickType click) {
        autoCraft.edit(player, this, slot, cursor, click);
    }

    @Override
    public void onDrag(Player player, List<Integer> slots, ItemStack cursor) {
        autoCraft.drag(player, this, slots, cursor);
    }

    public void render() {
        @Nullable ItemStack[] matrix = group.matrix();
        SlotMatch[] matches = group.matches();
        for (int i = 0; i < 9; i++) {
            ItemStack ghost = matrix[i];
            inventory.setItem(i + 1, ghost == null ? null : icon(ghost, matches[i].effective(autoCraft.hasRecipeChoice(group, i))));
        }
        ItemStack result = group.result();
        inventory.setItem(RESULT_SLOT, result == null ? null : withLore(result, messages.lines(Message.AUTOCRAFT_EDITOR_RESULT)));
    }

    private ItemStack icon(ItemStack ghost, SlotMatch match) {
        Message line = switch (match) {
            case RECIPE -> Message.AUTOCRAFT_MATCH_RECIPE;
            case EXACT -> Message.AUTOCRAFT_MATCH_EXACT;
            case TYPE -> Message.AUTOCRAFT_MATCH_TYPE;
        };
        List<Component> lore = new ArrayList<>(messages.lines(line));
        lore.addAll(messages.lines(Message.AUTOCRAFT_EDITOR_CONTROLS));
        return withLore(ghost, lore);
    }

    /** A copy of {@code item} with its lore replaced, so the icon is never similar to a real item. */
    private static ItemStack withLore(ItemStack item, List<Component> lore) {
        ItemStack icon = item.clone();
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return icon;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
