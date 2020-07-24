package com.jamesdpeters.minecraft.chests.runnables;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.misc.Settings;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class VirtualChestToHopper extends BukkitRunnable {

    private ChestLinkStorage storage;
    private BukkitTask task;

    public VirtualChestToHopper(ChestLinkStorage storage){
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
        for(LocationInfo location : storage.getLocations()) {
            if(location != null) {
                if (location.getLocation() != null) {
                    if(!Settings.isRunHoppersInUnloadedChunks() && !Utils.isLocationChunkLoaded(location.getLocation())) continue;
                    Location below = location.getLocation().clone().subtract(0, 1, 0);
                    if (below.getBlock().getState() instanceof Hopper) {
                        Hopper hopper = (Hopper) below.getBlock().getState();
                        if (below.getBlock().isBlockIndirectlyPowered() || below.getBlock().isBlockPowered()) {
                            continue;
                        }
                        int hopperAmount = SpigotConfig.getWorldSettings(location.getLocation().getWorld()).getHopperAmount();
                        if(Utils.moveToOtherInventory(storage.getInventory(), hopperAmount, hopper.getInventory(), HopperFilter.getHopperFilters(below.getBlock()))){
                            storage.updateDisplayItem();
                        }
                        if(storage.getInventory().getViewers().size() > 0) storage.sort();
                    }
                }
            }
        }
    }
}
