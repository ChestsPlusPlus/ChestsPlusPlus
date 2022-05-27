package com.jamesdpeters.minecraft.chests.v1_18_R2;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.EntityEventListener;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import com.jamesdpeters.minecraft.chests.v1_17_R1.EntityEventListener_1_17;
import com.jamesdpeters.minecraft.chests.v1_17_R1.MaterialChecker_1_17_R1;
import org.bukkit.Chunk;
import org.bukkit.block.Lidded;
import org.bukkit.craftbukkit.v1_18_R2.CraftChunk;
import org.bukkit.entity.ItemFrame;

import java.lang.reflect.Method;

public class NMSProviderImpl implements NMSProvider {

    @Override
    public ChestOpener getChestOpener() {
        return (inventory, container, tileEntityOpener) -> {
            if(container instanceof Lidded){
                if(inventory.getViewers().size() > 0){
                    ((Lidded) container).open();
                } else {
                    ((Lidded) container).close();
                }
            }
            return null;
        };
    }

    @Override
    public MaterialChecker getMaterialChecker() {
        return new MaterialChecker_1_17_R1();
    }

    @Override
    public CraftingProvider getCraftingProvider() {
        return new Crafting();
    }

    @Override
    public EntityEventListener getEntityEventListener() {
        return new EntityEventListener_1_17();
    }

    @Override
    public boolean isEntitiesLoadedOnChunk(Chunk chunk) {
        return chunk.isEntitiesLoaded();
    }

    @Override
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        itemFrame.setVisible(visible);
    }

}
