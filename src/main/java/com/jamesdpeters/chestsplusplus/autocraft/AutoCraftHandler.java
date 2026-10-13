package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.core.Viewers;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** AutoCraft's group type hooks: crafting tables, the recipe's result as icon and display item, and the recipe editor as its view. */
public final class AutoCraftHandler extends GroupTypeHandler<AutoCraftGroup> {

    private final Services services;
    private final Recipes recipes;
    private final RecipeEditor editor;
    private final Crafter crafter;

    public AutoCraftHandler(Services services, Recipes recipes, RecipeEditor editor, Crafter crafter) {
        super(GroupType.AUTOCRAFT, AutoCraftGroup.class);
        this.services = services;
        this.recipes = recipes;
        this.editor = editor;
        this.crafter = crafter;
    }

    @Override
    public boolean isValidBlock(Block block) {
        return block.getType() == Material.CRAFTING_TABLE;
    }

    @Override
    public AutoCraftGroup create(long id, UUID owner, String name) {
        return new AutoCraftGroup(id, owner, name, System.currentTimeMillis());
    }

    /** The group change that linking announces has already woken the new crafter. */
    @Override
    protected int linked(AutoCraftGroup group, Block block) {
        return 0;
    }

    @Override
    protected void removed(AutoCraftGroup group, Location dropAt) {
        recipes.forget(group);
        crafter.forget(group);
        RecipeEditor.viewers(group).forEach(Player::closeInventory);
    }

    @Override
    protected void renamed(AutoCraftGroup group) {
        RecipeEditor.viewers(group).forEach(Player::closeInventory);
    }

    @Override
    public void closeAllViews() {
        Viewers.withHolder(RecipeEditorHolder.class).forEach(Player::closeInventory);
    }

    @Override
    protected void revokedViews(AutoCraftGroup group) {
        RecipeEditor.viewers(group).stream().filter(player -> !services.access().canAccess(player.getUniqueId(), player, group))
                .forEach(Player::closeInventory);
    }

    @Override
    protected void openRemotely(Player player, AutoCraftGroup group) {
        editor.open(player, group);
    }

    @Override
    protected ItemStack iconOf(AutoCraftGroup group) {
        ItemStack result = group.result();
        return result == null ? ItemStack.of(Material.CRAFTING_TABLE) : result.asOne();
    }

    @Override
    protected String summaryOf(AutoCraftGroup group) {
        ItemStack result = group.result();
        if (result == null) return "no recipe";
        return result.getAmount() + "x " + itemName(result);
    }

    static String itemName(ItemStack item) {
        return item.getType().getKey().getKey().replace('_', ' ');
    }

    @Override
    protected @Nullable ItemStack itemOf(AutoCraftGroup group) {
        return group.result();
    }

    @Override
    protected Component labelOf(AutoCraftGroup group) {
        return services.messages().get(Message.AUTOCRAFT_DISPLAY_LABEL, Messages.group(group));
    }
}
