package com.jamesdpeters.chestsplusplus.autocraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.jspecify.annotations.Nullable;

/**
 * The crafting operations AutoCraft needs from the server. {@link #bukkit()} is the real implementation; tests supply
 * their own because MockBukkit doesn't implement recipe matching.
 */
public interface CraftingBackend {

    /**
     * A recipe resolved from a ghost matrix: its key, its result, and for each of the 9 slots the recipe's own choice that accepts the
     * ghost (e.g. any planks). A slot is null when it's empty or the recipe exposes no choice there, as with special recipes.
     */
    record ResolvedRecipe(@Nullable NamespacedKey key, ItemStack result, List<@Nullable Predicate<ItemStack>> choices) {}

    /** The result of actually crafting a planned matrix: output and leftovers (e.g. empty buckets). */
    record Crafted(ItemStack result, @Nullable ItemStack[] remaining) {}

    @Nullable
    ResolvedRecipe resolve(@Nullable ItemStack[] matrix, World world);

    @Nullable
    Crafted craft(@Nullable ItemStack[] matrix, World world);

    static CraftingBackend bukkit() {
        return new CraftingBackend() {
            @Override
            public @Nullable ResolvedRecipe resolve(@Nullable ItemStack[] matrix, World world) {
                Recipe recipe = Bukkit.getCraftingRecipe(copy(matrix), world);
                if (recipe == null) return null;
                ItemCraftResult result = Bukkit.craftItemResult(copy(matrix), world);
                if (result.getResult().isEmpty()) return null;
                NamespacedKey key = recipe instanceof Keyed keyed ? keyed.getKey() : null;
                return new ResolvedRecipe(key, result.getResult(), slotChoices(recipe, matrix));
            }

            @Override
            public @Nullable Crafted craft(@Nullable ItemStack[] matrix, World world) {
                ItemCraftResult result = Bukkit.craftItemResult(copy(matrix), world);
                if (result.getResult().isEmpty()) return null;
                return new Crafted(result.getResult(), result.getResultingMatrix());
            }
        };
    }

    private static ItemStack[] copy(@Nullable ItemStack[] matrix) {
        ItemStack[] copy = new ItemStack[9];
        for (int i = 0; i < 9 && i < matrix.length; i++) copy[i] = matrix[i] == null ? null : matrix[i].clone();
        return copy;
    }

    /** Per slot, the recipe choice that accepts the ghost item, so e.g. any planks can stand in for oak planks in a tag recipe. */
    static List<@Nullable Predicate<ItemStack>> slotChoices(Recipe recipe, @Nullable ItemStack[] matrix) {
        List<RecipeChoice> choices = new ArrayList<>();
        switch (recipe) {
            case ShapedRecipe shaped -> choices.addAll(shaped.getChoiceMap().values().stream().filter(Objects::nonNull).toList());
            case ShapelessRecipe shapeless -> choices.addAll(shapeless.getChoiceList());
            case TransmuteRecipe transmute -> {
                choices.add(transmute.getInput());
                choices.add(transmute.getMaterial());
            }
            default -> {}
        }
        List<@Nullable Predicate<ItemStack>> slots = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ItemStack ghost = i < matrix.length ? matrix[i] : null;
            if (ghost == null || ghost.isEmpty()) {
                slots.add(null);
                continue;
            }
            slots.add(choices.stream().filter(choice -> choice.test(ghost)).<Predicate<ItemStack>>map(choice -> choice).findFirst().orElse(null));
        }
        return slots;
    }
}
