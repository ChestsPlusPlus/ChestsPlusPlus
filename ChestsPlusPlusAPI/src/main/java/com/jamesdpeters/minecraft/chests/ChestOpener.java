package com.jamesdpeters.minecraft.chests;

import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;

public interface ChestOpener {
    void setLidOpen(Inventory inventory, Container chest, boolean open);
}
