package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorageType;
import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class LinkedChestHopperListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void fromHopper(InventoryMoveItemEvent event) {
        //FROM HOPPER
        if (event.getInitiator().getHolder() instanceof Hopper) {
            Location location = event.getDestination().getLocation();
            if (location == null) return;
            if (!Utils.isLocationChunkLoaded(location)) return;

            ChestLinkStorageType storageType = Config.getChestLink();
            if (storageType == null) return;
            ChestLinkStorage storage = storageType.getStorage(location);
            if (storage != null) {
                if(!event.isCancelled()) {
                    event.setCancelled(true);
                    new BukkitRunnable() {
                        @Override
                        public void run() {

                            int hopperAmount = SpigotConfig.getWorldSettings(location.getWorld()).getHopperAmount();
                            if (Utils.hopperMove(event.getSource(), hopperAmount, storage.getInventory())) {
                                storage.updateDisplayItem();
                            }
                            if (event.getDestination().getHolder() != null) event.getDestination().getHolder().getInventory().clear();
                            if (storage.getInventory().getViewers().size() > 0) storage.sort();
                        }
                    }.runTaskLater(ChestsPlusPlus.PLUGIN, 1);
                }
            }
        }
    }
}
