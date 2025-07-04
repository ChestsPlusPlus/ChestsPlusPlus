package com.jamesdpeters.minecraft.chests.filters;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class HopperFilter {

    public static boolean isInFilter(List<Filter> filters, ItemStack item) {
        if (filters == null) return true;
        if (filters.size() == 0) return true;
        List<Filter> acceptFilters = filters.stream().filter(filter -> filter.getFilteringMethod() == Filter.Type.ACCEPT).collect(Collectors.toList());
        boolean hasAcceptFilter = acceptFilters.stream().anyMatch(filter -> filter.getFilterType(item).equals(Filter.Type.ACCEPT));
        boolean isRejected = filters.stream().filter(filter -> filter.getFilteringMethod() == Filter.Type.REJECT).anyMatch(filter -> filter.getFilterType(item).equals(Filter.Type.REJECT));
        if (acceptFilters.size() > 0) return hasAcceptFilter && !isRejected;
        else return !isRejected;
    }

    public static List<Filter> getHopperFilters(Block block) {
        Collection<Entity> ent = block.getWorld().getNearbyEntities(block.getLocation(), 1.01, 1.01, 1.01);
        List<Filter> filters = new ArrayList<>(ent.size());
        for (Entity entity : ent) {
            if (entity instanceof ItemFrame frame) {
                if (frame.getItem().getType().equals(Material.AIR)) continue;
                Block attachedBlock = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
                if (block.equals(attachedBlock)) {
                    filters.add(new Filter(frame.getItem(), frame));
                }
            }
        }
        return filters;
    }

    public static boolean hasFilters(Block block) {
        return getHopperFilters(block).size() > 0;
    }

    public static boolean isInFilter(Block block, ItemStack itemStack) {
        return isInFilter(getHopperFilters(block), itemStack);
    }
}
