package com.jamesdpeters.minecraft.chests.storage.autocraft;

import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.PluginConfig;
import com.jamesdpeters.minecraft.chests.serialize.RecipeSerializable;
import com.jamesdpeters.minecraft.chests.storage.abstracts.AbstractStorage;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Map;

@SerializableAs("AutoCraftingStorage")
public class AutoCraftingStorage extends AbstractStorage implements ConfigurationSerializable {

    private RecipeSerializable recipeSerializable;
    private String identifier;
    private VirtualCraftingHolder virtualCraftingHolder;

    public AutoCraftingStorage(Map<String, Object> map) {
        super(map);
    }

    @Override
    public AutoCraftingStorageType getStorageType() {
        return Config.getAutoCraft();
    }

    public AutoCraftingStorage(OfflinePlayer player, String identifier, Location location, Location signLocation) {
        super(player, identifier, location, signLocation);
        this.identifier = identifier;
        initInventory();
    }

    @Override
    protected void serialize(Map<String, Object> hashMap) {
        hashMap.put("recipe", recipeSerializable);
        hashMap.put("identifier", identifier);
    }

    @Override
    protected void deserialize(Map<String, Object> map) {
        recipeSerializable = (RecipeSerializable) map.get("recipe");
        identifier = (String) map.get("identifier");
        initInventory();
    }

    @Override
    public boolean storeInventory() {
        return false;
    }

    public void setRecipe(Recipe recipe) {
        if (recipe == null) {
            recipeSerializable = null;
            return;
        }
        recipeSerializable = new RecipeSerializable(recipe);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean shouldDisplayArmourStands() {
        return PluginConfig.DISPLAY_AUTOCRAFT_ARMOUR_STAND.get();
    }

    @Override
    public boolean doesDropInventory() {
        return false;
    }

    @Override
    public double getBlockOffset(Block block) {
        return -0.07;
    }

    public VirtualCraftingHolder getVirtualCraftingHolder() {
        return virtualCraftingHolder;
    }

    @Override
    protected Inventory initInventory() {
        if (virtualCraftingHolder == null) virtualCraftingHolder = new VirtualCraftingHolder(this);

        if (recipeSerializable != null) {
            Recipe recipe = recipeSerializable.getRecipe();

            if (recipe instanceof ShapelessRecipe) {
                virtualCraftingHolder.setCrafting((ShapelessRecipe) recipe);
            }
            if (recipe instanceof ShapedRecipe) {
                virtualCraftingHolder.setCrafting((ShapedRecipe) recipe);
            }
        } else {
            virtualCraftingHolder.resetChoices();
        }

        virtualCraftingHolder.setUpdatingRecipe(false);
        virtualCraftingHolder.updateGUI();
        return virtualCraftingHolder.getInventory();
    }

    @Override
    protected void setIdentifier(String newIdentifier) {
        identifier = newIdentifier;
    }

    @Override
    public void onStorageAdded(Block block, Player player) {
        //Don't need to do anything with the Crafting table.
    }

    @Override
    public void postConfigLoad() {
        super.postConfigLoad();
        if (recipeSerializable != null && recipeSerializable.getRecipe() != null)
            onItemDisplayUpdate(recipeSerializable.getRecipe().getResult());
    }
}
