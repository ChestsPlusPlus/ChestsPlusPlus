package com.jamesdpeters.chestsplusplus.autocraft;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jspecify.annotations.Nullable;

/** Builds a ghost matrix from a registered recipe, for groups that only know their recipe's key. */
public final class RecipeMatrix {

    private RecipeMatrix() {}

    /** The 3x3 matrix, or null when no shaped or shapeless recipe is registered under the key. */
    public static @Nullable ItemStack @Nullable [] of(NamespacedKey key) {
        return switch (Bukkit.getRecipe(key)) {
            case ShapedRecipe shaped -> shaped(shaped);
            case ShapelessRecipe shapeless -> shapeless(shapeless.getChoiceList());
            case Recipe _ -> null;
            case null -> null;
        };
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
