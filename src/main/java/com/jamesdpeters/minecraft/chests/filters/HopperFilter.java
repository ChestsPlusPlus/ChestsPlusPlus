package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HopperFilter {

    public static boolean isInFilter(List<Filter> filters, ItemStack item){
        if(filters == null) return true;
        if(filters.size() == 0) return true;
        for(Filter filter : filters){
            if(filter.isFiltered(item)) return true;
        }
        return false;
    }

    public static List<Filter> getHopperFilters(Block block){
        Collection<Entity> ent = block.getLocation().getWorld().getNearbyEntities(block.getLocation(),1.01,1.01,1.01);
        List<Filter> filters = new ArrayList<>(ent.size());
        for(Entity entity : ent){
            if(entity instanceof ItemFrame){
                ItemFrame frame = (ItemFrame) entity;
                Block attachedBlock = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
                if(block.equals(attachedBlock)){
                    boolean isFilteredByItemMeta = true;
                    Rotation rotation = frame.getRotation();
                    if(rotation.equals(Rotation.FLIPPED)) isFilteredByItemMeta = false;
                    filters.add(new Filter(frame.getItem(),isFilteredByItemMeta));
                }
            }
        }
        return filters;
    }

    public static boolean isInFilter(Block block, ItemStack itemStack){
        return isInFilter(getHopperFilters(block),itemStack);
    }
}
