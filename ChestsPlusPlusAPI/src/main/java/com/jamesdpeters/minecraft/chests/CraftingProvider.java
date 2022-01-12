package com.jamesdpeters.minecraft.chests;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public interface CraftingProvider {

    CraftingResult craft(World world, ItemStack[] items);

    Recipe getRecipe(Player player, World world, ItemStack[] items);
}
