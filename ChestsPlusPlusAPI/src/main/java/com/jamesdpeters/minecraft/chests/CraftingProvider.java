package com.jamesdpeters.minecraft.chests;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public interface CraftingProvider {

    CraftingResult craft(Player player, World world, ItemStack[] items);

    Recipe getRecipe(World world, ItemStack[] items);
}
