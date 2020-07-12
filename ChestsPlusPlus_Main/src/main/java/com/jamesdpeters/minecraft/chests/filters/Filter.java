package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.Rotation;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

public class Filter {

    private ItemStack filter;
    private boolean filterByItemMeta;
    private boolean dontAllowThisItem;

    public Filter(ItemStack filter, ItemFrame itemFrame){
        this.filter = filter;
        this.filterByItemMeta = itemFrame.getRotation().equals(Rotation.FLIPPED) || itemFrame.getRotation().equals(Rotation.COUNTER_CLOCKWISE);
        this.dontAllowThisItem = itemFrame.getRotation().equals(Rotation.CLOCKWISE) || itemFrame.getRotation().equals(Rotation.COUNTER_CLOCKWISE);
    }

    public boolean isFiltered(ItemStack itemStack){
        if(dontAllowThisItem && !filterByItemMeta) return !filter.isSimilar(itemStack);
        else if (dontAllowThisItem) return !isFilteredByMeta(itemStack);
        return isFilteredByMeta(itemStack);
    }

    private boolean isFilteredByMeta(ItemStack itemStack){
        if(filter.isSimilar(itemStack)) return true;
        if(filterByItemMeta){
            return filter.getType().equals(itemStack.getType());
        }
        return false;
    }
}
