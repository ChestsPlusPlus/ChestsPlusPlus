package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.Config;
import com.jamesdpeters.minecraft.chests.Utils;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.jamesdpeters.minecraft.chests.Utils.hasFilter;

public class HopperListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperMoveEvent(InventoryMoveItemEvent event) {
        //TO HOPPER
        if(event.getDestination().getHolder() instanceof Hopper){
            event.setCancelled(!isItemInFilter(event.getDestination().getLocation().getBlock(),event.getItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void fromHopper(InventoryMoveItemEvent event){
        //FROM HOPPER
        if (event.getInitiator().getHolder() instanceof Hopper) {
            InventoryStorage storage = Config.getInventoryStorage(event.getDestination().getLocation());
            if (storage != null) {
                if(!event.isCancelled()) {
                    event.setCancelled(true);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Utils.moveToOtherInventory(event.getSource(), 1, storage.getInventory());
                            event.getDestination().getHolder().getInventory().clear();
                        }
                    }.runTaskLater(ChestsPlusPlus.PLUGIN, 1);
                }
            }
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event){
        if(event.getInventory().getHolder() instanceof Hopper){
            event.setCancelled(!isItemInFilter(event.getInventory().getLocation().getBlock(),event.getItem().getItemStack()));
        }
    }

    public static boolean isItemInFilter(Block block, ItemStack item){
        Collection<Entity> ent = block.getLocation().getWorld().getNearbyEntities(block.getLocation(),1.01,1.01,1.01);
        boolean hasFilter = false;
        for(Entity frame : ent){
            if(frame instanceof ItemFrame){
                Block attachedBlock = frame.getLocation().getBlock().getRelative(((ItemFrame) frame).getAttachedFace());
                if(block.equals(attachedBlock)){
                    if(((ItemFrame) frame).getItem().getType() != Material.AIR) hasFilter = true;
                    if(item.isSimilar(((ItemFrame) frame).getItem())){
                        return true;
                    }
                }
            }
        }
        if(!hasFilter) return true;
        return false;
    }



}
