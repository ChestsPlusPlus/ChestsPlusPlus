package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.sort.InventorySorter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.InventoryHolder;

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryClickEvent event) {
        inventoryUpdate(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        try {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder instanceof VirtualInventoryHolder vHolder) {
                vHolder.openPreviousInventory();
                if (vHolder.didPlayerRemoteOpen(event.getPlayer().getUniqueId())) {
                    Utils.closeInventorySound((Player) event.getPlayer(), event.getInventory());
                }
                event.getViewers().remove(event.getPlayer());
                Utils.closeStorageInventory(vHolder.getStorage());
                vHolder.getStorage().onItemDisplayUpdate(InventorySorter.getMostCommonItem(event.getInventory()));
            }
        } catch (NullPointerException ignore) {
        } //Essentials does something weird with enderchests - shit fix but works :)
    }

    public void inventoryUpdate(InventoryInteractEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof VirtualInventoryHolder vHolder) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> {
                vHolder.getStorage().sort();
                vHolder.getStorage().onItemDisplayUpdate(InventorySorter.getMostCommonItem(event.getInventory()));
            }, 1);
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryDragEvent event) {
        inventoryUpdate(event);
    }
}
