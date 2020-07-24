package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.misc.Settings;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class HopperListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperMoveEvent(InventoryMoveItemEvent event) {
        //TO HOPPER
        if(event.getDestination().getHolder() instanceof Hopper){
            if(event.getDestination().getLocation() != null){
                if(event.getDestination().getLocation().getBlock().isBlockPowered()) return;
            }
            event.setCancelled(!HopperFilter.isInFilter(event.getDestination().getLocation().getBlock(),event.getItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void fromHopper(InventoryMoveItemEvent event){
        //FROM HOPPER
        if (event.getInitiator().getHolder() instanceof Hopper) {
            Location location = event.getDestination().getLocation();
            ChestLinkStorage storage = Config.getChestLink().getStorage(location);
            if (storage != null) {
                if(!event.isCancelled()) {
                    event.setCancelled(true);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if(location != null) {
                                int hopperAmount = SpigotConfig.getWorldSettings(location.getWorld()).getHopperAmount();
                                if (Utils.moveToOtherInventory(event.getSource(), hopperAmount, storage.getInventory())) {
                                    storage.updateDisplayItem();
                                }
                                if (event.getDestination().getHolder() != null) event.getDestination().getHolder().getInventory().clear();
                                if (storage.getInventory().getViewers().size() > 0) storage.sort();
                            }
                        }
                    }.runTaskLater(ChestsPlusPlus.PLUGIN, 1);
                }
            }
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event){
        if(event.getInventory().getHolder() instanceof Hopper){
            event.setCancelled(!HopperFilter.isInFilter(event.getInventory().getLocation().getBlock(), event.getItem().getItemStack()));
        }
    }

    @EventHandler
    public void itemFrameInteract(PlayerInteractEntityEvent event){
        if(event.isCancelled()) return;
        if(event.getRightClicked().getType().equals(EntityType.ITEM_FRAME)){
            ItemFrame itemFrame = (ItemFrame) event.getRightClicked();
            Block attachedBlock = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());
            if(!(attachedBlock.getState() instanceof Hopper)) return;
            Rotation rotation = itemFrame.getRotation();

            //Set ItemFrame invisible based on config.
            ApiSpecific.getNmsProvider().setItemFrameVisible(itemFrame, !Settings.isFilterItemFrameInvisible());

            //ItemFrame event acts weird, it returns the values of the itemframe *before* the event. So we have to calculate what the next state will be.
            if(!itemFrame.getItem().getType().equals(Material.AIR)) rotation = rotation.rotateClockwise();

            if(rotation.equals(Rotation.FLIPPED)){
                event.getPlayer().sendMessage(ChatColor.AQUA+"ItemFrame now filters all types of this item! e.g Enchanted Books.");
            } else if(rotation.equals(Rotation.NONE)) {
                event.getPlayer().sendMessage(ChatColor.GREEN+"ItemFrame is in default filtering mode. Rotate Item Frame to change mode!");
            } else if(rotation.equals(Rotation.CLOCKWISE)) {
                event.getPlayer().sendMessage(ChatColor.DARK_RED+"ItemFrame now prevents this item from being accepted in the hopper!");
            } else if(rotation.equals(Rotation.COUNTER_CLOCKWISE)) {
                event.getPlayer().sendMessage(ChatColor.GOLD+"ItemFrame now prevents all types of this item from being accepted in the hopper! e.g Enchanted Books.");
            }
        }
    }
}
