package com.jamesdpeters.minecraft.chests.interfaces;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

public class InventoryViewImpl extends InventoryView {

    Inventory top, bottom;
    HumanEntity humanEntity;

    public InventoryViewImpl(Inventory top, Inventory bottom, HumanEntity humanEntity){
        this.top = top;
        this.bottom = bottom;
        this.humanEntity = humanEntity;
    }

    @NotNull
    @Override
    public Inventory getTopInventory() {
        return top;
    }

    @NotNull
    @Override
    public Inventory getBottomInventory() {
        return bottom;
    }

    @NotNull
    @Override
    public HumanEntity getPlayer() {
        return humanEntity;
    }

    @NotNull
    @Override
    public InventoryType getType() {
        return InventoryType.WORKBENCH;
    }

    @NotNull
    @Override
    public String getTitle() {
        return "ChestsPlusPlusInventoryView";
    }
}
