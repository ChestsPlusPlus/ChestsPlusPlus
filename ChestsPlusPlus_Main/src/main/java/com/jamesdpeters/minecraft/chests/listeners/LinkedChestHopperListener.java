package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.ServerType;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.PluginConfig;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Function;

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
