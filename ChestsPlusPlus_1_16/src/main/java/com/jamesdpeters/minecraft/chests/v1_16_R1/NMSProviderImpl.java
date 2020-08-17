package com.jamesdpeters.minecraft.chests.v1_16_R1;

import com.jamesdpeters.minecraft.chests.ChestOpener;
import com.jamesdpeters.minecraft.chests.MaterialChecker;
import com.jamesdpeters.minecraft.chests.NMSProvider;
import com.jamesdpeters.minecraft.chests.TileEntityOpener;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;

public class NMSProviderImpl implements NMSProvider {

    @Override
    public ChestOpener getChestOpener() {
        return (inventory, container, tileEntityOpener) -> {
            if(hasLiddedAPI()){
                if(container instanceof Lidded){
                    if(inventory.getViewers().size() > 0){
                        ((Lidded) container).open();
                    } else {
                        ((Lidded) container).close();
                    }
                }
            }
            return null;
        };
    }

    @Override
    public MaterialChecker getMaterialChecker() {
        return new MaterialChecker_1_16();
    }

    @Override
    public void setItemFrameVisible(ItemFrame itemFrame, boolean visible) {
        itemFrame.setVisible(visible);
    }

    private boolean hasLiddedAPI(){
        try {
            Class.forName("org.bukkit.block.Lidded");
            return true;
        } catch (ClassNotFoundException e){
            return false;
        }
    }
}
