package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.PluginConfig;
import com.jamesdpeters.minecraft.chests.api.ApiSpecific;
import com.jamesdpeters.minecraft.chests.filters.HopperFilter;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.ServerType;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.SpigotConfig;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
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

import java.util.function.Function;

public class FilterHopperListener implements Listener {

    @EventHandler
    public void itemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;
        if (event.getRightClicked().getType().equals(EntityType.ITEM_FRAME)) {
            ItemFrame itemFrame = (ItemFrame) event.getRightClicked();
            Block attachedBlock = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());
            if (!(attachedBlock.getState() instanceof Hopper)) return;
            Rotation rotation = itemFrame.getRotation();

            //Set ItemFrame invisible based on config.
            ApiSpecific.getNmsProvider().setItemFrameVisible(itemFrame, !PluginConfig.INVISIBLE_FILTER_ITEM_FRAMES.get());

            //ItemFrame event acts weird, it returns the values of the itemframe *before* the event. So we have to calculate what the next state will be.
            if (!itemFrame.getItem().getType().equals(Material.AIR)) rotation = rotation.rotateClockwise();

            if (rotation.equals(Rotation.FLIPPED)) {
                event.getPlayer().sendMessage(ChatColor.AQUA + Message.ITEM_FRAME_FILTER_ALL_TYPES.getString());
            } else if (rotation.equals(Rotation.NONE)) {
                event.getPlayer().sendMessage(ChatColor.GREEN + Message.ITEM_FRAME_FILTER_DEFAULT.getString());
            } else if (rotation.equals(Rotation.CLOCKWISE)) {
                event.getPlayer().sendMessage(ChatColor.DARK_RED + Message.ITEM_FRAME_FILTER_DENY.getString());
            } else if (rotation.equals(Rotation.COUNTER_CLOCKWISE)) {
                event.getPlayer().sendMessage(ChatColor.GOLD + Message.ITEM_FRAME_FILTER_DENY_ALL_TYPES.getString());
            }
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (event.getInventory().getHolder() instanceof Hopper) {
            event.setCancelled(!HopperFilter.isInFilter(event.getInventory().getLocation().getBlock(), event.getItem().getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperMoveEvent(InventoryMoveItemEvent event) {
        //TO HOPPER
        if(event.getDestination().getHolder() instanceof Hopper) {
            if(event.getDestination().getLocation() != null) {
                // If the event is cancelled by other plugin
                if(event.isCancelled()) return;
                if(event.getDestination().getLocation().getBlock().isBlockPowered()) return;
            }

            Function<ItemStack, Boolean> isFilteredItem = (itemStack ->
                    HopperFilter.isInFilter(event.getDestination().getLocation().getBlock(), itemStack));

            event.setCancelled(!isFilteredItem.apply(event.getItem()));

            // Item shouldn't be allowed
            if (event.isCancelled() && ServerType.getType() == ServerType.Type.PAPER) {
                int index = event.getSource().first(event.getItem());
                int hopperAmount = SpigotConfig.getWorldSettings(event.getSource().getLocation()).getHopperAmount();
                // Loop over the inventory until next item is found, if no item found return.
                while (true) {
                    if (index >= event.getSource().getSize()) {
                        return;
                    }

                    ItemStack item = event.getSource().getItem(index++);

                    if (item == null) {
                        continue;
                    }

                    if (isFilteredItem.apply(item)) {
                        Utils.hopperMove(event.getSource(), item, hopperAmount, event.getDestination());
                        return;
                    }
                }
            }
        }
    }
}
