package com.jamesdpeters.chestsplusplus.model;

import java.util.Arrays;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** An AutoCrafter group: every node crafts the same 3x3 recipe. */
public final class AutoCraftGroup extends StorageGroup {

    private final @Nullable ItemStack[] matrix = new ItemStack[9];
    private final SlotMatch[] matches = new SlotMatch[9];
    private @Nullable NamespacedKey recipeKey;
    private @Nullable ItemStack result;

    public AutoCraftGroup(long id, UUID owner, String name, long createdAt) {
        super(id, owner, name, createdAt);
        Arrays.fill(matches, SlotMatch.RECIPE);
    }

    @Override
    public GroupType type() {
        return GroupType.AUTOCRAFT;
    }

    /** A copy of the 3x3 ghost-item matrix (row-major, nulls for empty slots). */
    public @Nullable ItemStack[] matrix() {
        @Nullable ItemStack[] copy = new ItemStack[9];
        for (int i = 0; i < 9; i++) copy[i] = matrix[i] == null ? null : matrix[i].clone();
        return copy;
    }

    /** Sets the matrix; a slot whose ghost changes goes back to {@link SlotMatch#RECIPE}. */
    public void setRecipe(@Nullable ItemStack[] newMatrix, @Nullable NamespacedKey key, @Nullable ItemStack result) {
        if (newMatrix.length != 9) throw new IllegalArgumentException("Matrix must have 9 slots");
        for (int i = 0; i < 9; i++) {
            ItemStack item = newMatrix[i];
            ItemStack ghost = item == null || item.isEmpty() ? null : item.asOne();
            if (ghost == null ? matrix[i] != null : !ghost.isSimilar(matrix[i])) matches[i] = SlotMatch.RECIPE;
            matrix[i] = ghost;
        }
        this.recipeKey = key;
        this.result = result == null || result.isEmpty() ? null : result.clone();
    }

    /** A copy of each slot's match mode. */
    public SlotMatch[] matches() {
        return matches.clone();
    }

    public void setMatch(int slot, SlotMatch match) {
        matches[slot] = match;
    }

    public @Nullable NamespacedKey recipeKey() {
        return recipeKey;
    }

    /** The current recipe's output, or null when the matrix is not a valid recipe. */
    public @Nullable ItemStack result() {
        return result == null ? null : result.clone();
    }

    public boolean hasRecipe() {
        return result != null;
    }

    public boolean matrixIsEmpty() {
        return Arrays.stream(matrix).allMatch(item -> item == null);
    }
}
