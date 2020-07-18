package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualCraftingHolder;
import com.jamesdpeters.minecraft.chests.sort.InventorySorter;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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
                    ChestLinkStorage storage = Config.getChestLink().getStorage(event.getInventory().getLocation());
                    if (storage != null) {
                        event.setCancelled(true);
                        if (event.getPlayer().hasPermission(Permissions.OPEN) && storage.hasPermission((Player) event.getPlayer())) {
                            storage.getInventory().getViewers().remove(event.getPlayer());
                            Utils.openChestInventory((Player) event.getPlayer(), storage, event.getInventory().getLocation());
                        } else {
                            Messages.NO_PERMISSION((Player) event.getPlayer());
                        }
                    } else {
                        //If no Inventory Storage here check for AutoCraft
                        AutoCraftingStorage craftingStorage = Config.getAutoCraft().getStorage(event.getInventory().getLocation());

                        if(craftingStorage != null){
                            event.setCancelled(true);
                            if(event.getPlayer().hasPermission(Permissions.AUTOCRAFT_OPEN) && craftingStorage.hasPermission((Player) event.getPlayer())) {
                                event.getPlayer().openInventory(craftingStorage.getInventory());
                                craftingStorage.getVirtualCraftingHolder().startAnimation();
                            }
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
                if (vHolder.didPlayerRemoteOpen(event.getPlayer().getUniqueId())) {
                    Utils.closeInventorySound((Player) event.getPlayer(), event.getInventory());
                }
                event.getViewers().remove(event.getPlayer());
                vHolder.getStorage().getLocations().forEach(locationInfo -> {
                    Block block = locationInfo.getLocation().getBlock();
                    if(block.getState() instanceof Container){
                        Container chest = (Container) block.getState();
                        Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> ApiSpecific.getChestOpener().setLidOpen(event.getInventory(),chest,false),1);
                    }
                });
                vHolder.getStorage().onItemDisplayUpdate(InventorySorter.getMostCommonItem(event.getInventory()));
            }
            if(holder instanceof VirtualCraftingHolder){
                ((VirtualCraftingHolder) holder).stopAnimation();
            }
        } catch (NullPointerException ignore){} //Essentials does something weird with enderchests - shit fix but works :)
    }

    public void inventoryUpdate(InventoryInteractEvent event){
        InventoryHolder holder = event.getInventory().getHolder();
        if(holder instanceof VirtualInventoryHolder){
            VirtualInventoryHolder vHolder = (VirtualInventoryHolder) holder;
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, () -> {
                vHolder.getStorage().sort();
                vHolder.getStorage().onItemDisplayUpdate(InventorySorter.getMostCommonItem(event.getInventory()));
            },1);
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
            Player p = (Player) event.getWhoClicked();
//            Bukkit.broadcastMessage(event.getRawSlots().toString());
            for(int slot : event.getRawSlots()) {
                if(slot >= p.getOpenInventory().getTopInventory().getSize())
                    continue;

//                Bukkit.broadcastMessage("Drag! "+slot+" cursor: "+event.getOldCursor());
                setCraftingItem(event.getInventory(),slot,event.getOldCursor());
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftingPlayerUpdate(InventoryClickEvent event){
        Player player = (Player) event.getWhoClicked();

        if(event.getView().getTopInventory().getHolder() instanceof VirtualCraftingHolder){
//            Bukkit.broadcastMessage("Click: "+event.getAction());

            if(event.getAction() == InventoryAction.COLLECT_TO_CURSOR ||
                    event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                    event.getAction() == InventoryAction.NOTHING) {
//                Bukkit.broadcastMessage("Cancelled!");
                event.setCancelled(true);
                player.updateInventory();
                return;
            }

            if(event.getClickedInventory() == player.getOpenInventory().getTopInventory()){
//                Bukkit.broadcastMessage("Clicked: "+event.getSlot());
                if(event.getSlot() == 0) event.setCancelled(true);
                if(event.getSlot() >= 1 && event.getSlot() <= 9){
                    setCraftingItem(event.getInventory(),event.getSlot(),event.getCursor());
                    event.setCancelled(true);
                    craftingUpdate(event);
                }
            }
        }
    }

    private void setCraftingItem(Inventory inventory, int slot, ItemStack cursor){
        ItemStack clone = null;
        if(cursor != null){
            clone = cursor.clone();
            clone.setAmount(1);
        }
        inventory.setItem(slot,clone);
    }

    private void craftingUpdate(InventoryInteractEvent event){
        InventoryHolder holder = event.getInventory().getHolder();
        if(holder instanceof VirtualCraftingHolder){
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, (((VirtualCraftingHolder) holder).setUpdatingRecipe(true))::updateCrafting,1);
            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestsPlusPlus.PLUGIN, (((VirtualCraftingHolder) holder))::forceUpdateInventory,1);
        }
    }



}
