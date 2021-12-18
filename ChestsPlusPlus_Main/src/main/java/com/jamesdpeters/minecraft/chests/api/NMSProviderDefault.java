package com.jamesdpeters.minecraft.chests.api;

import com.jamesdpeters.minecraft.chests.*;
import com.jamesdpeters.minecraft.chests.latest.NMSProviderImpl;
import org.bukkit.entity.ItemFrame;

public class NMSProviderDefault implements NMSProvider {

    //Latest version at time of build is 1.17
    //All new versions should be able to run with the default provided after 1.17
    NMSProviderImpl defaultProvider;

    public NMSProviderDefault() {
        defaultProvider = new NMSProviderImpl();
    }

    @Override
    public ChestOpener getChestOpener() {
        //1.16 ChestOpener contains lidded API!
        return defaultProvider.getChestOpener();
    }

    @Override
    public MaterialChecker getMaterialChecker() {
        //Return the current latest MaterialChecker when an newer server implementation is found.
        return defaultProvider.getMaterialChecker();
    }

    @Override
    public CraftingProvider getCraftingProvider() {
        return defaultProvider.getCraftingProvider();
    }

    @Override
    public EntityEventListener getEntityEventListener() {
        return defaultProvider.getEntityEventListener();
    }

    @Override
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        //Not supported in Bukkit api 1.14.
        defaultProvider.setItemFrameVisible(itemFrame, visible);
    }
}
