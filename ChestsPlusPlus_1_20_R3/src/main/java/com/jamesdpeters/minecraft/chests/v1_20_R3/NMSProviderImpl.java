package com.jamesdpeters.minecraft.chests.v1_20_R3;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.CraftingProvider;
import com.jamesdpeters.minecraft.chests.EntityEventListener;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import com.jamesdpeters.minecraft.chests.v1_17_R1.EntityEventListener_1_17;
import com.jamesdpeters.minecraft.chests.v1_17_R1.MaterialChecker_1_17_R1;
import org.bukkit.block.Lidded;
import org.bukkit.entity.ItemFrame;

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
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        itemFrame.setVisible(visible);
    }

}