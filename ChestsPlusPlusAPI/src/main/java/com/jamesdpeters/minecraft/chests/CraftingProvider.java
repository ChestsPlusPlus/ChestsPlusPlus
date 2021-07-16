package com.jamesdpeters.minecraft.chests;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;

public interface CraftingProvider {

    CraftingResult craft(Player player, World world, List<ItemStack> items);

    Recipe getRecipe(Player player, World world, List<ItemStack> items);
}
