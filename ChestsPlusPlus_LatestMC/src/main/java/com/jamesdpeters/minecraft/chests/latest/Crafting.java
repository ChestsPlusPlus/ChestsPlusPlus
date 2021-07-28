package com.jamesdpeters.minecraft.chests.latest;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public class Crafting implements CraftingProvider {

    @Override
    public CraftingResult craft(Player player, World world, ItemStack[] items) {
        var item = Bukkit.craftItem(items, world, player);
        CraftingResult result = new CraftingResult();
        result.setResult(item);
        result.setMatrixResult(items);
        return result;
    }

    @Override
    public Recipe getRecipe(Player player, World world, ItemStack[] items) {
        return Bukkit.getCraftingRecipe(items, world);
    }
}
