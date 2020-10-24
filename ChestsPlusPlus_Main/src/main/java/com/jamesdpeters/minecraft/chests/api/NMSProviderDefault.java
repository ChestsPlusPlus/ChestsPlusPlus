package com.jamesdpeters.minecraft.chests.api;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import com.jamesdpeters.minecraft.chests.v1_16_R1.NMSProviderImpl;
import org.bukkit.entity.ItemFrame;

public class NMSProviderDefault implements NMSProvider {

    //Latest version at time of build is 1.16
    NMSProviderImpl provider1_16;

    public NMSProviderDefault() {
        provider1_16 = new NMSProviderImpl();
    }

    @Override
    public ChestOpener getChestOpener() {
        //1.16 ChestOpener contains lidded API!
        return provider1_16.getChestOpener();
    }

    @Override
    public MaterialChecker getMaterialChecker() {
        //Return the current latest MaterialChecker when an newer server implementation is found.
        return provider1_16.getMaterialChecker();
    }

    @Override
    public CraftingProvider getCraftingProvider() {
        // TODO
        return null;
    }

    @Override
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        //Not supported in Bukkit api 1.14.
        provider1_16.setItemFrameVisible(itemFrame, visible);
    }
}
