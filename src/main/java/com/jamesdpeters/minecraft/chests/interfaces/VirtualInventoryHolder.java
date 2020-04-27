package com.jamesdpeters.minecraft.chests.interfaces;

import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VirtualInventoryHolder implements InventoryHolder {

    InventoryStorage storage;

    private Runnable openPreviousInventory;
    private boolean isPrevInvRunning = false;

    public VirtualInventoryHolder(InventoryStorage storage){
        this.storage = storage;
    }

    @Override
    public Inventory getInventory() {
        return storage.getInventory();
    }

    public InventoryStorage getStorage(){
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
