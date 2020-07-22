package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.Rotation;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

public class Filter {

    enum Type {
        ACCEPT,
        REJECT,
        NONE
    }

    private ItemStack filter;
    private boolean filterByItemMeta;
    private boolean dontAllowThisItem;
    private Type filteringMethod;

    public Filter(ItemStack filter, ItemFrame itemFrame){
        this.filter = filter;
        this.filterByItemMeta = itemFrame.getRotation().equals(Rotation.FLIPPED) || itemFrame.getRotation().equals(Rotation.COUNTER_CLOCKWISE);
        this.dontAllowThisItem = itemFrame.getRotation().equals(Rotation.CLOCKWISE) || itemFrame.getRotation().equals(Rotation.COUNTER_CLOCKWISE);
        filteringMethod = dontAllowThisItem ? Type.REJECT : Type.ACCEPT;
    }

    public Type getFilterType(ItemStack itemStack){
        if(dontAllowThisItem && !filterByItemMeta){
            if(filter.isSimilar(itemStack)) return Type.REJECT;
            else return Type.ACCEPT;
        }
        else if (dontAllowThisItem){
             if(isFilteredByMeta(itemStack)) return Type.REJECT;
             else return Type.ACCEPT;
        }
        if(isFilteredByMeta(itemStack)) return Type.ACCEPT;
        return Type.NONE;
    }

    private boolean isFilteredByMeta(ItemStack itemStack){
        if(filter.isSimilar(itemStack)) return true;
        if(filterByItemMeta){
            return filter.getType().equals(itemStack.getType());
        }
        return false;
    }

    public Type getFilteringMethod(){
        return filteringMethod;
    }
}
