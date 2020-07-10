package com.jamesdpeters.minecraft.chests;

import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;

public interface ChestOpener {
    void setLidOpen(Inventory inventory, Chest chest, boolean open);
}
