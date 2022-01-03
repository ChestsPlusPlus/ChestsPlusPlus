package com.jamesdpeters.minecraft.chests.latest;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.CraftingResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Arrays;

public class Crafting implements CraftingProvider {

    @Override
    public CraftingResult craft(Player player, World world, ItemStack[] items) {
        // Create copy of array since Bukkit#craftItem modifies the input array.
        ItemStack[] itemsCopy = Arrays.copyOf(items, items.length);

        var item = Bukkit.craftItem(itemsCopy, world, player);
        return new CraftingResult(item, itemsCopy);
    }

    @Override
    public Recipe getRecipe(World world, ItemStack[] items) {
        return Bukkit.getCraftingRecipe(items, world);
    }
}
