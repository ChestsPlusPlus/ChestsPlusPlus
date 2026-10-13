package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.Items;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.core.Viewers;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Opens the recipe editor and applies its clicks. Anyone with access may look, but only managers may change the recipe. */
@RequiredArgsConstructor
public final class RecipeEditor {

    private final Services services;
    private final Checks checks;
    private final Recipes recipes;

    public void open(Player player, AutoCraftGroup group) {
        Component title = services.messages().get(Message.MENU_RECIPE_TITLE, Messages.group(group));
        player.openInventory(new RecipeEditorHolder(group, title, this, recipes, services.messages()).getInventory());
    }

    /** Right-clicking a ghost with an empty cursor changes what that slot accepts; any other click places or clears a ghost. */
    void edit(Player player, RecipeEditorHolder editor, int slot, @Nullable ItemStack cursor, ClickType click) {
        AutoCraftGroup group = editor.group();
        if (checks.refused(player, checks.manage(player, group))) return;
        if (click.isRightClick() && !Items.present(cursor)) {
            int index = RecipeEditorHolder.matrixIndex(slot);
            if (index >= 0 && group.matrix()[index] != null) {
                recipes.setMatch(group, index, group.matches()[index].next(recipes.hasRecipeChoice(group, index)));
            }
            return;
        }
        applyEdit(player, editor, editor.click(slot, cursor));
    }

    /** Dragging an item across the editor places a ghost in each matrix slot it covered, resolved once. */
    void drag(Player player, RecipeEditorHolder editor, List<Integer> slots, ItemStack cursor) {
        if (!checks.refused(player, checks.manage(player, editor.group()))) applyEdit(player, editor, editor.place(slots, cursor));
    }

    private void applyEdit(Player player, RecipeEditorHolder editor, @Nullable ItemStack @Nullable [] matrix) {
        if (matrix == null) return;
        AutoCraftGroup group = editor.group();
        ItemStack result = recipes.setMatrix(group, matrix, player);
        editor.render();
        if (result != null) {
            services.send(player, Message.AUTOCRAFT_RECIPE_SET, Messages.group(group), Messages.text("item", AutoCraftHandler.itemName(result)));
        }
    }

    /** Redraws every editor open on {@code group}. */
    void render(AutoCraftGroup group) {
        for (Player viewer : viewers(group)) {
            if (Viewers.topHolder(viewer) instanceof RecipeEditorHolder holder) holder.render();
        }
    }

    static List<Player> viewers(StorageGroup group) {
        return Viewers.withHolder(RecipeEditorHolder.class, editor -> editor.group().id() == group.id());
    }
}
