package com.jamesdpeters.minecraft.chests.interfaces;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VirtualCraftingHolder implements InventoryHolder {

    private Inventory inventory;

    public VirtualCraftingHolder(){
        inventory = Bukkit.createInventory(this, InventoryType.WORKBENCH);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
