package com.jamesdpeters.minecraft.chests;

import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;

public interface ChestOpener {
    TileEntityOpener updateState(Inventory inventory, Container chest, TileEntityOpener tileEntityOpener);
}
