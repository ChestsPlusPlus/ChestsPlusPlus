package com.jamesdpeters.minecraft.chests.storage;

import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import com.jamesdpeters.minecraft.chests.serialize.RecipeSerializable;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Map;
import java.util.UUID;

@SerializableAs("AutoCraftingStorage")
public class AutoCraftingStorage extends AbstractStorage implements ConfigurationSerializable  {

    private RecipeSerializable recipeSerializable;
    private String identifier;
    private VirtualCraftingHolder virtualCraftingHolder;

    @Override
    void serialize(Map<String, Object> hashMap) {
        hashMap.put("recipe",recipeSerializable);
        hashMap.put("playerUUID",playerUUID.toString());
        hashMap.put("identifier", identifier);
        hashMap.put("isPublic", isPublic);
    }

    @Override
    void deserialize(Map<String, Object> map) {
        recipeSerializable = (RecipeSerializable) map.get("recipe");
        playerUUID = UUID.fromString((String) map.get("playerUUID"));
        identifier = (String) map.get("identifier");

        if(map.containsKey("isPublic")) isPublic = (boolean) map.get("isPublic");
        else isPublic = false;

        initInventory();
    }

    @Override
    ItemStack getArmorStandItem() {
        if(recipeSerializable != null){
            if(recipeSerializable.getRecipe() != null){
                return recipeSerializable.getRecipe().getResult();
            }
        }
        return null;
    }


    @Override
    boolean storeInventory() {
        return false;
    }

    public AutoCraftingStorage(OfflinePlayer player, String identifier, Location location, StorageType<AutoCraftingStorage> storageType){
        super(player, identifier, location, storageType);
        this.playerUUID = player.getUniqueId();
        this.identifier = identifier;
        this.isPublic = false;
        initInventory();
    }

    public void setRecipe(Recipe recipe){
        if(recipe == null){
            recipeSerializable = null;
            return;
        }
        recipeSerializable = new RecipeSerializable(recipe);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public VirtualCraftingHolder getVirtualCraftingHolder() {
        return virtualCraftingHolder;
    }

    @Override
    protected Inventory initInventory(){
        virtualCraftingHolder = new VirtualCraftingHolder(this);

        if(recipeSerializable != null) {
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
    void setIdentifier(String newIdentifier) {
        identifier = newIdentifier;
    }

    @Override
    void onStorageAdded(Block block, Player player) {
        //Don't need to do anything with the Crafting table.
    }
}
