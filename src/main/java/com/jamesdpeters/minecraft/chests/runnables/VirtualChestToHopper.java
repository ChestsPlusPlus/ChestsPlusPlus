package com.jamesdpeters.minecraft.chests.runnables;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.Utils;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class VirtualChestToHopper extends BukkitRunnable {

    InventoryStorage storage;
    BukkitTask task;

    public VirtualChestToHopper(InventoryStorage storage){
        this.storage = storage;
    }

    public void start(){
        task = runTaskTimer(ChestsPlusPlus.PLUGIN,1,8);
    }

    public void stop(){
        task.cancel();
    }

    @Override
    public void run() {
        for(Location location : storage.getLocations()) {
            if(location != null) {
                Location below = location.clone().subtract(0, 1, 0);
                if (below.getBlock().getState() instanceof Hopper) {
                    Hopper hopper = (Hopper) below.getBlock().getState();
                    if(below.getBlock().isBlockIndirectlyPowered()|| below.getBlock().isBlockPowered()){
                        continue;
                    }
                    Utils.moveToOtherInventory(storage.getInventory(), 1, hopper.getInventory(), Utils.getHopperFilters(below.getBlock()));
                }
            }
        }
    }
}
