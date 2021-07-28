package com.jamesdpeters.minecraft.chests;

import org.bukkit.inventory.ItemStack;

public class CraftingResult {
    ItemStack result;
    ItemStack[] matrixResult;

    public CraftingResult(ItemStack result, ItemStack[] matrixResult) {
        this.result = result;
        this.matrixResult = matrixResult;
    }

    public CraftingResult() {}

    public ItemStack[] getMatrixResult() {
        return matrixResult;
    }

    public void setMatrixResult(ItemStack[] matrixResult) {
        this.matrixResult = matrixResult;
    }

    public ItemStack getResult() {
        return result;
    }

    public void setResult(ItemStack result) {
        this.result = result;
    }
}
