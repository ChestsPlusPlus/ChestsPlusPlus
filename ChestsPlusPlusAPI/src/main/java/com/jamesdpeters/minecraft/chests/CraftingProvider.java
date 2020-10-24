package com.jamesdpeters.minecraft.chests;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;

public interface CraftingProvider {

    ItemStack craft(World world, List<ItemStack> items);

    Recipe getRecipe(World world, List<ItemStack> items);
}
