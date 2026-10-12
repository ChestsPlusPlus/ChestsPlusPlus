package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.autocraft.RecipeMatrix;
import com.jamesdpeters.chestsplusplus.core.Items;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Rebuilds an AutoCraft ghost matrix from a v2 recipe. v2 kept the items only for recipes that are neither shaped nor shapeless. */
final class V2Recipes {

    private V2Recipes() {}

    /**
     * The 3x3 matrix, or null when the recipe isn't registered (yet) and v2 kept no items for it. AutoCraft rebuilds such a matrix
     * from the key if the recipe appears by the time the server has loaded.
     */
    static @Nullable ItemStack @Nullable [] matrix(V2Data.Recipe recipe) {
        return recipe.items() != null ? fromItems(recipe.items()) : RecipeMatrix.of(recipe.key());
    }

    private static @Nullable ItemStack[] fromItems(@Nullable ItemStack[] items) {
        @Nullable ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9 && i < items.length; i++) matrix[i] = Items.present(items[i]) ? items[i].asOne() : null;
        return matrix;
    }
}
