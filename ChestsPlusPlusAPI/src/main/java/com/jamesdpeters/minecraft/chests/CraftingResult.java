package com.jamesdpeters.minecraft.chests;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public record CraftingResult(ItemStack result, ItemStack[] matrixResult, List<ItemStack> overflowItems) {

    public void setResultMatrix(int i, ItemStack asBukkitCopy) {
        matrixResult[i] = Objects.requireNonNullElseGet(asBukkitCopy, () -> new ItemStack(Material.AIR));
    }
}
