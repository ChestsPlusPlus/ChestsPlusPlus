package com.jamesdpeters.minecraft.chests.v1_21_R1;

import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
public class NMSProviderImpl implements NMSProvider {

    @Override
    public MaterialChecker getMaterialChecker() {
        return new MaterialChecker_1_21_R1();
    }

    @Override
    public CraftingProvider getCraftingProvider() {
        return new Crafting();
    }

}