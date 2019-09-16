package com.jamesdpeters.minecraft.chests.listeners;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.jamesdpeters.minecraft.chests.Config;
import com.jamesdpeters.minecraft.chests.Utils;
import com.jamesdpeters.minecraft.chests.interfaces.VirtualInventoryHolder;
import com.jamesdpeters.minecraft.chests.protocollib.WrapperPlayServerBlockAction;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPlayerUpdate(InventoryClickEvent event){
        if(event.getInventory().getHolder() instanceof VirtualInventoryHolder){
            Config.save();
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event){
        if(event.getInventory().getLocation() != null){
            InventoryStorage storage = Config.getInventoryStorage(event.getInventory().getLocation());
            if(storage != null){
                event.setCancelled(true);

                Location chestLocation = event.getInventory().getLocation();
                BlockPosition blockPosition = new BlockPosition((int) chestLocation.getX(),(int)chestLocation.getY(),(int)chestLocation.getZ());

                WrapperPlayServerBlockAction packet = new WrapperPlayServerBlockAction();
                packet.setLocation(blockPosition);
                packet.setBlockType(event.getInventory().getLocation().getBlock().getType());
                packet.setByte1(1);
                packet.setByte2(1);

                packet.broadcastPacket();
                packet.sendPacket((Player) event.getPlayer());

                Utils.openInventory((Player) event.getPlayer(),storage.getInventory());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event){
        if(event.getInventory().getLocation() == null){
            Utils.closeInventorySound((Player) event.getPlayer(),event.getInventory());
        }
        if(event.getInventory().getHolder() instanceof VirtualInventoryHolder){
            Config.save();
        }
    }

}
