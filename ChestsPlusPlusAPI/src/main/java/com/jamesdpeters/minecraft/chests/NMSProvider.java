package com.jamesdpeters.minecraft.chests;

import org.bukkit.Chunk;
import org.bukkit.entity.ItemFrame;

public interface NMSProvider {
    ChestOpener getChestOpener();
    MaterialChecker getMaterialChecker();
    CraftingProvider getCraftingProvider();
    EntityEventListener getEntityEventListener();
    boolean isEntitiesLoadedOnChunk(Chunk chunk);

    void setItemFrameVisible(ItemFrame itemFrame, boolean visible);
}
