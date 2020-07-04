package com.jamesdpeters.minecraft.chests.serialize;

import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.LinkedHashMap;
import java.util.Map;

@SerializableAs("Recipe")
public class RecipeSerializable implements ConfigurationSerializable {

    private Recipe recipe;
    private NamespacedKey key;

    public RecipeSerializable(Recipe recipe){
        this.recipe = recipe;
        if(recipe instanceof ShapedRecipe) key = ((ShapedRecipe) recipe).getKey();
        else if(recipe instanceof ShapelessRecipe) key = ((ShapelessRecipe) recipe).getKey();
        else throw new IllegalArgumentException("Recipe type has not been implemented! "+recipe.getClass().toGenericString());
    }

    public RecipeSerializable(Map<String, Object> map){
        //noinspection deprecation
        key = new NamespacedKey((String) map.get("namespace"), (String) map.get("key"));
        recipe = Crafting.getRecipeByKey(key);
    }

    @Override
    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("namespace",key.getNamespace());
        map.put("key",key.getKey());
        return map;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public NamespacedKey getKey() {
        return key;
    }
}
