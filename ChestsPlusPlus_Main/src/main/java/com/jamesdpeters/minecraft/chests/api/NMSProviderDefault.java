package com.jamesdpeters.minecraft.chests.api;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import com.jamesdpeters.minecraft.chests.v1_16_R1.MaterialChecker_1_16;
import org.bukkit.entity.ItemFrame;

public class NMSProviderDefault implements NMSProvider {
    @Override
    public ChestOpener getChestOpener() {
        return (storage, chest, open) -> {
            //Default to doing nothing.
        };
    }

    @Override
    public MaterialChecker getMaterialChecker() {
        //Return the current latest MaterialChecker when an newer server implementation is found.
        return new MaterialChecker_1_16();
    }

    @Override
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        //Not supported in Bukkit api 1.14.
    }
}
