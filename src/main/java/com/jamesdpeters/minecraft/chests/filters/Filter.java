package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.inventory.ItemStack;

public class Filter {

    private ItemStack filter;
    private boolean filterByItemMeta;

    public Filter(ItemStack filter, boolean filterByItemMeta){
        this.filter = filter;
        this.filterByItemMeta = filterByItemMeta;
    }

    public boolean isFiltered(ItemStack itemStack){
        if(filter.isSimilar(itemStack)) return true;
        if(!filterByItemMeta){
            return filter.getType().equals(itemStack.getType());
        }
        return false;
    }
}
