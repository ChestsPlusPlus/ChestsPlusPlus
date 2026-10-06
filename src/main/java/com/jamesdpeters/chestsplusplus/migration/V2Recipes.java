package com.jamesdpeters.chestsplusplus.migration;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jspecify.annotations.Nullable;

/** Rebuilds an AutoCraft ghost matrix from a v2 recipe. v2 kept the items only for recipes that are neither shaped nor shapeless. */
final class V2Recipes {

    private V2Recipes() {}

    /** The 3x3 matrix, or null when the recipe no longer exists and v2 kept no items for it. */
    static @Nullable ItemStack @Nullable [] matrix(V2Data.Recipe recipe) {
        if (recipe.items() != null) return fromItems(recipe.items());
        return switch (Bukkit.getRecipe(recipe.key())) {
            case ShapedRecipe shaped -> shaped(shaped);
            case ShapelessRecipe shapeless -> shapeless(shapeless.getChoiceList());
            case Recipe _ -> null;
            case null -> null;
        };
    }

    private static @Nullable ItemStack[] fromItems(@Nullable ItemStack[] items) {
        @Nullable ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9 && i < items.length; i++) matrix[i] = items[i] == null || items[i].isEmpty() ? null : items[i].asOne();
        return matrix;
    }

    /** The shape is placed top-left; AutoCraft matches shapes wherever they sit in the grid. */
    private static @Nullable ItemStack[] shaped(ShapedRecipe recipe) {
        @Nullable ItemStack[] matrix = new ItemStack[9];
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        for (int row = 0; row < shape.length && row < 3; row++) {
            for (int column = 0; column < shape[row].length() && column < 3; column++) {
                matrix[row * 3 + column] = ghost(choices.get(shape[row].charAt(column)));
            }
        }
        return matrix;
    }

    private static @Nullable ItemStack[] shapeless(List<RecipeChoice> choices) {
        @Nullable ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9 && i < choices.size(); i++) matrix[i] = ghost(choices.get(i));
        return matrix;
    }

    /** One item the choice accepts; the slot keeps {@code SlotMatch.RECIPE}, so the whole choice still matches. */
    private static @Nullable ItemStack ghost(@Nullable RecipeChoice choice) {
        return switch (choice) {
            case RecipeChoice.MaterialChoice materials -> new ItemStack(materials.getChoices().getFirst());
            case RecipeChoice.ExactChoice exact -> exact.getChoices().getFirst().asOne();
            case null -> null;
            default -> null;
        };
    }
}
