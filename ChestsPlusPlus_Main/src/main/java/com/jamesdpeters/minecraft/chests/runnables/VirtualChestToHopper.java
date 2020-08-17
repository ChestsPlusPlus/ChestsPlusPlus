package com.jamesdpeters.minecraft.chests.runnables;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.serialize.PluginConfig;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class VirtualChestToHopper extends BukkitRunnable {

    private final ChestLinkStorage storage;
    private BukkitTask task;

    public VirtualChestToHopper(ChestLinkStorage storage) {
        this.storage = storage;
    }

    public void start() {
        task = runTaskTimer(ChestsPlusPlus.PLUGIN, 1, 8);
    }

    public void stop() {
        task.cancel();
    }

    @Override
    public void run() {
        for (LocationInfo location : storage.getLocations()) {
            if (location != null) {
                if (location.getLocation() != null) {
                    if (!PluginConfig.SHOULD_RUN_HOPPERS_UNLOADED_CHUNKS.get() && !Utils.isLocationChunkLoaded(location.getLocation()))
                        continue;
                    Location below = location.getLocation().clone().subtract(0, 1, 0);
                    if (below.getBlock().getState() instanceof Hopper) {
                        Hopper hopper = (Hopper) below.getBlock().getState();
                        if (below.getBlock().isBlockIndirectlyPowered() || below.getBlock().isBlockPowered()) {
                            continue;
                        }
                        if (move(hopper.getLocation(), storage.getInventory(), hopper.getInventory())) {
                            storage.updateDisplayItem();
                        }
                        if (storage.getInventory().getViewers().size() > 0) storage.sort();
                    }
                }
            }
        }
    }

    public static boolean move(Location targetLocation, Inventory source, Inventory target) {
        int hopperAmount = SpigotConfig.getWorldSettings(targetLocation.getWorld()).getHopperAmount();
        return Utils.hopperMove(source, hopperAmount, target, HopperFilter.getHopperFilters(targetLocation.getBlock()));
    }
}
