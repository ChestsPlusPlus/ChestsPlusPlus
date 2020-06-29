package com.jamesdpeters.minecraft.chests.interfaces;

import com.jamesdpeters.minecraft.chests.storage.ChestLinkStorage;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VirtualInventoryHolder implements InventoryHolder {

    ChestLinkStorage storage;

    private Runnable openPreviousInventory;
    private boolean isPrevInvRunning = false;

    public VirtualInventoryHolder(ChestLinkStorage storage){
        this.storage = storage;
    }

    @Override
    public Inventory getInventory() {
        return storage.getInventory();
    }

    public ChestLinkStorage getStorage(){
        return storage;
    }

    public void setPreviousInventory(Runnable runnable){
        openPreviousInventory = runnable;
        isPrevInvRunning = false;
    }

    /**
     * @return true if previous inventory existed.
     */
    public boolean openPreviousInventory(){
        if(isPrevInvRunning){
            isPrevInvRunning = false;
            return false;
        }
        if(openPreviousInventory != null){
            isPrevInvRunning = true;
            openPreviousInventory.run();
            openPreviousInventory = null;
            return true;
        }
        return false;
    }
}
