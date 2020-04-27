package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.misc.Config;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitScheduler;

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryClickEvent event){
        if(event.getInventory().getHolder() instanceof VirtualInventoryHolder){
            inventoryUpdate(event);
            Config.save();
        }
    }

    @EventHandler (priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event){
        try {
            if (event.getPlayer() instanceof Player) {
                if (event.getInventory().getLocation() != null) {
                    InventoryStorage storage = Config.getInventoryStorage(event.getInventory().getLocation());
                    if (storage != null) {
                        event.setCancelled(true);
                        if (event.getPlayer().hasPermission(Permissions.OPEN) && storage.hasPermission((Player) event.getPlayer())) {
                            Utils.openInventory((Player) event.getPlayer(), storage.getInventory());
                        } else {
                            Messages.NO_PERMISSION((Player) event.getPlayer());
                        }
                    }
                }
            }
        } catch (NullPointerException ignore){} //Essentials does something weird with enderchests - shit fix but works :)
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event){
        try {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder instanceof VirtualInventoryHolder) {
                VirtualInventoryHolder vHolder = (VirtualInventoryHolder) holder;
                vHolder.openPreviousInventory();
                if (event.getInventory().getLocation() == null) {
                    Utils.closeInventorySound((Player) event.getPlayer(), event.getInventory());
                }
                Config.save();
            }
        } catch (NullPointerException ignore){} //Essentials does something weird with enderchests - shit fix but works :)
    }

    public void inventoryUpdate(InventoryInteractEvent event){
        if(event.getInventory().getHolder() instanceof VirtualInventoryHolder){
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> ((VirtualInventoryHolder) event.getInventory().getHolder()).getStorage().sort(),1);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryDragEvent event){
        if(event.getInventory().getHolder() instanceof VirtualInventoryHolder){
            inventoryUpdate(event);
        }
    }


}
