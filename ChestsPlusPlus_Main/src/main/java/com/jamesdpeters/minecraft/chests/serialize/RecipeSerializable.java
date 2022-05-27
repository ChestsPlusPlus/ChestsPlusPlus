package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SerializableAs("C++Recipe")
public class RecipeSerializable implements ConfigurationSerializable {

    private Recipe recipe;
    private NamespacedKey namespacedKey;

    // Store items used for ComplexRecipes
    private ItemStack[] items;

    public RecipeSerializable(Recipe recipe, ItemStack[] items) {
        this.recipe = recipe;
        this.items = items;
        if (recipe instanceof Keyed){
            namespacedKey = ((Keyed) recipe).getKey();
        }
    }

    public RecipeSerializable(Map<String, Object> map) {
        Object obj = map.get("items");
        if (obj != null) {
            if (obj instanceof ItemStack[] itemArray) {
                items = itemArray;
            } else {
                //noinspection unchecked
                items = ((List<ItemStack>)obj).toArray(new ItemStack[9]);
            }
        }

        //noinspection deprecation
        namespacedKey = new NamespacedKey((String) map.get("namespace"), (String) map.get("key"));
        recipe = Crafting.getRecipeByKey(namespacedKey);
    }

    public void updateRecipe(Player player) {
        if (recipe == null) {
            recipe = ApiSpecific.getNmsProvider().getCraftingProvider().getRecipe(player, Bukkit.getWorlds().get(0), items);
        }
    }

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("namespace", namespacedKey.getNamespace());
        map.put("key", namespacedKey.getKey());
        if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
            map.put("items", items);
        }
        return map;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public NamespacedKey getNamespacedKey() {
        return namespacedKey;
    }

    public ItemStack[] getItems() {
        return items;
    }
}
