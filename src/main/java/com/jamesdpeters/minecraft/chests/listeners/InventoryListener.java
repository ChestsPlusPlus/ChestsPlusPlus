package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import com.jamesdpeters.minecraft.chests.serialize.Config;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryClickEvent event){
        inventoryUpdate(event);
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
            }
        } catch (NullPointerException ignore){} //Essentials does something weird with enderchests - shit fix but works :)
    }

    public void inventoryUpdate(InventoryInteractEvent event){
        InventoryHolder holder = event.getInventory().getHolder();
        if(holder instanceof VirtualInventoryHolder){
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> ((VirtualInventoryHolder) event.getInventory().getHolder()).getStorage().sort(),1);
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryDragEvent event){
        inventoryUpdate(event);
        craftingUpdate(event);
    }


    //CRAFTING
    @EventHandler(priority = EventPriority.HIGHEST)
    public void inventoryDragEvent(InventoryDragEvent event){
        Inventory inventory = event.getInventory();
        if(inventory.getHolder() instanceof VirtualCraftingHolder){
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftingPlayerUpdate(InventoryClickEvent event){
        Inventory inventory = event.getClickedInventory();
        if(inventory != null && event.getInventory().getHolder() instanceof VirtualCraftingHolder){
            if(event.getClick() == ClickType.DOUBLE_CLICK){
                if(event.getClickedInventory() == event.getView().getBottomInventory()){
                    event.getInventory().setItem(event.getSlot(),event.getCurrentItem());
                    event.setCurrentItem(null);
                }
                event.setCancelled(true);
            }
            if(event.getClickedInventory() == event.getView().getTopInventory()){
                if(event.getSlot() >= 1 && event.getSlot() <= 9){
                    Bukkit.broadcastMessage("Click: "+event.getSlot());
                    event.getClickedInventory().setItem(event.getSlot(),event.getCurrentItem());
                }
                event.setCancelled(true);
                craftingUpdate(event);
            }
        }
    }

    private void craftingUpdate(InventoryInteractEvent event){
        InventoryHolder holder = event.getInventory().getHolder();
        if(holder instanceof VirtualCraftingHolder){
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> Crafting.updateCrafting(holder.getInventory()),1);
        }
    }



}
