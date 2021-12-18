package com.jamesdpeters.minecraft.chests;

import org.bukkit.entity.ItemFrame;

public interface NMSProvider {
    ChestOpener getChestOpener();
    MaterialChecker getMaterialChecker();
    CraftingProvider getCraftingProvider();
    EntityEventListener getEntityEventListener();

    void setItemFrameVisible(ItemFrame itemFrame, boolean visible);
}
