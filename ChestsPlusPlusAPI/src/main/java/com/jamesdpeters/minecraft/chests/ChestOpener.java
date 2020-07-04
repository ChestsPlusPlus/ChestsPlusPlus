package com.jamesdpeters.minecraft.chests;

import org.bukkit.block.Chest;

public interface ChestOpener {
    void setLidOpen(Chest chest, boolean open);
}
