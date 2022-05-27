package com.jamesdpeters.minecraft.chests;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public class CraftingResult {

    private ItemStack result;
    private ItemStack[] matrixResult;
    private List<ItemStack> overflowItems;

    public CraftingResult(ItemStack result, ItemStack[] matrixResult, List<ItemStack> overflowItems) {
        this.result = result;
        this.matrixResult = matrixResult;
        this.overflowItems = overflowItems;
    }

    public void setResultMatrix(int i, ItemStack asBukkitCopy) {
        if (asBukkitCopy == null) asBukkitCopy = new ItemStack(Material.AIR);
        matrixResult[i] = asBukkitCopy;
    }

    public List<ItemStack> getOverflowItems() {
        return overflowItems;
    }

    public ItemStack getResult() {
        return result;
    }

    public ItemStack[] getMatrixResult() {
        return matrixResult;
    }
}
