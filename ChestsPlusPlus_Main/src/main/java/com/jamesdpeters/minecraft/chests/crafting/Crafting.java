package com.jamesdpeters.minecraft.chests.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Crafting {

    private static List<ShapedRecipe> shapedRecipes;
    private static List<ShapelessRecipe> shapelessRecipes;

    public static void load() {
        shapedRecipes = new ArrayList<>();
        shapelessRecipes = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof ShapedRecipe) {
                shapedRecipes.add((ShapedRecipe) recipe);
            }
            if (recipe instanceof ShapelessRecipe) {
                shapelessRecipes.add((ShapelessRecipe) recipe);
            }
        });
    }

    public static Recipe getResult(List<ItemStack> craftingTable) {
        Recipe returnRecipe = null;
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof ShapedRecipe) {
                ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
                if (matchesShaped(shapedRecipe, craftingTable)) {
                    returnRecipe = shapedRecipe;
                    break;
                }
            } else if (recipe instanceof ShapelessRecipe) {
                ShapelessRecipe shapelessRecipe = (ShapelessRecipe) recipe;
                if (matchesShapeless(shapelessRecipe.getChoiceList(), craftingTable)) {
                    returnRecipe = shapelessRecipe;
                    break;
                }
            }
        }
        return returnRecipe;
    }

    private static boolean matchesShapeless(List<RecipeChoice> choice, List<ItemStack> items) {
        items = new ArrayList<>(items);
        for (RecipeChoice c : choice) {
            boolean match = false;
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (item == null || item.getType() == Material.AIR)
                    continue;
                if (c.test(item)) {
                    match = true;
                    items.remove(i);
                    break;
                }
            }
            if (!match)
                return false;
        }
        Set<ItemStack> remainingItems = new HashSet<>(items);
        return (remainingItems.size() == 1 && (items.contains(new ItemStack(Material.AIR)) || items.contains(null)));
    }

    private static boolean matchesShaped(ShapedRecipe shape, List<ItemStack> items) {
        UserShapedRecipe userShapedRecipe = new UserShapedRecipe(items);
        return userShapedRecipe.matchesRecipe(shape);
    }

    public static Recipe getRecipeByKey(NamespacedKey key) {
        Optional<ShapelessRecipe> recipe = shapelessRecipes.stream().filter(s -> s.getKey().equals(key)).findFirst();
        if (recipe.isPresent()) return recipe.get();

        Optional<ShapedRecipe> shapedRecipe = shapedRecipes.stream().filter(s -> s.getKey().equals(key)).findFirst();
        return shapedRecipe.orElse(null);
    }

}
