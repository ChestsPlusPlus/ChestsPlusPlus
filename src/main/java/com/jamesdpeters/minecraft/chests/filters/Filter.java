package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.inventory.ItemStack;

public class Filter {

    private ItemStack filter;
    private boolean filterByItemMeta = true;

    public Filter(ItemStack filter, boolean filterByItemMeta){
        this.filter = filter;
        this.filterByItemMeta = filterByItemMeta;
    }

    public ItemStack getFilter() {
        return filter;
    }

    public boolean isFiltered(ItemStack itemStack){
        if(filter.isSimilar(itemStack)) return true;
        if(!filterByItemMeta){
            if(filter.getType().equals(itemStack.getType())) return true;
        }
        return false;
    }
}
