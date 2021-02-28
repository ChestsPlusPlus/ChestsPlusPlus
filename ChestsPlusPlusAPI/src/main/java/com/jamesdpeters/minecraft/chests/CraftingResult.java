package com.jamesdpeters.minecraft.chests;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftingResult {
    ItemStack result;
    List<ItemStack> matrixResult;

    public List<ItemStack> getMatrixResult() {
        return matrixResult;
    }

    public void setMatrixResult(List<ItemStack> matrixResult) {
        this.matrixResult = matrixResult;
    }

    public ItemStack getResult() {
        return result;
    }

    public void setResult(ItemStack result) {
        this.result = result;
    }
}
