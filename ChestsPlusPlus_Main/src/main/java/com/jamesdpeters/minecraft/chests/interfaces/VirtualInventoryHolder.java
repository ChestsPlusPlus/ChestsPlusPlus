package com.jamesdpeters.minecraft.chests.interfaces;

import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.UUID;

public class VirtualInventoryHolder implements InventoryHolder {

    ChestLinkStorage storage;

    private Runnable openPreviousInventory;
    private boolean isPrevInvRunning = false;
    private HashMap<UUID, Boolean> remoteOpened;

    public VirtualInventoryHolder(ChestLinkStorage storage){
        this.storage = storage;
        remoteOpened = new HashMap<>();
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

    /**
     * Called when a player remotely opens this inventory.
     * @param uuid - the players uuid.
     */
    public void onPlayerRemoteOpened(UUID uuid){
        remoteOpened.put(uuid,true);
    }

    /**
     * Checks if the player remotely opened this inventory, if so returns true and sets their remoteOpen to false.
     * @param uuid
     * @return
     */
    public boolean didPlayerRemoteOpen(UUID uuid){
        if(remoteOpened.getOrDefault(uuid,false)){
            remoteOpened.put(uuid,false);
            return true;
        }
        return false;
    }
}
