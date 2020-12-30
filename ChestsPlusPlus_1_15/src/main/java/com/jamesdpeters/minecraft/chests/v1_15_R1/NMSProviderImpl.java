package com.jamesdpeters.minecraft.chests.v1_15_R1;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import org.bukkit.entity.ItemFrame;

public class NMSProviderImpl implements NMSProvider {
    @Override
    public ChestOpener getChestOpener() {
        return new ChestOpener_1_15();
    }

    @Override
    public MaterialChecker getMaterialChecker() {
        return new MaterialChecker_1_15();
    }

    @Override
    public CraftingProvider getCraftingProvider() {
        return new Crafting();
    }

    @Override
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        //Not supported in 1.15
    }
}
