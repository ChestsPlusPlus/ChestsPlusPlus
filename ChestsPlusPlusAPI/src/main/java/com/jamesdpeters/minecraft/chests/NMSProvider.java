package com.jamesdpeters.minecraft.chests;

import org.bukkit.entity.ItemFrame;

public interface NMSProvider {
    ChestOpener getChestOpener();
    MaterialChecker getMaterialChecker();
    CraftingProvider getCraftingProvider();
    NPCProvider getNPCProvider();

    void setItemFrameVisible(ItemFrame itemFrame, boolean visible);
}
