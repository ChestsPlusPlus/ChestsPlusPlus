package com.jamesdpeters.minecraft.chests.interfaces;

import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VirtualInventoryHolder implements InventoryHolder {

    InventoryStorage storage;

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
}
