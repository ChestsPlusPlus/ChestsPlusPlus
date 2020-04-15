package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.*;
import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class ChestLinkListener implements Listener {

    @EventHandler
    public void playerInteract(BlockPlaceEvent event){
            if(event.getBlockPlaced().getState() instanceof Sign){
                if(event.getBlockAgainst().getState() instanceof Chest) {
                        new TempListener() {
                            @EventHandler
                            public void onSignChange(SignChangeEvent signChangeEvent) {
                                if (event.getBlockPlaced().getLocation().equals(signChangeEvent.getBlock().getLocation())) {
                                    Sign sign = (Sign) signChangeEvent.getBlock().getState();
                                    ChestLinkInfo info = Utils.getChestLinkInfo(sign, signChangeEvent.getLines(), signChangeEvent.getPlayer());
                                    if (info != null) {
                                        if(event.getPlayer().hasPermission(Permissions.ADD)) {
                                            if (Utils.isValidSignPosition(event.getBlockAgainst().getLocation())) {
                                                Config.addChest(info.getPlayer(), info.getGroup(), event.getBlockAgainst().getLocation());
                                                Messages.CHEST_ADDED(event.getPlayer(), info.getGroup(), event.getPlayer().getDisplayName());
                                                setLine(sign, signChangeEvent, 0, ChatColor.RED + ChatColor.stripColor(signChangeEvent.getLine(0)));
                                                setLine(sign, signChangeEvent, 1, ChatColor.GREEN + ChatColor.stripColor(signChangeEvent.getLine(1)));
                                                setLine(sign, signChangeEvent, 2, ChatColor.BOLD + ChatColor.stripColor(event.getPlayer().getDisplayName()));
                                                sign.getPersistentDataContainer().set(Values.playerUUID, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
                                                sign.update();
                                            } else {
                                                Messages.SIGN_FRONT_OF_CHEST(event.getPlayer());
                                            }
                                        } else {
                                            Messages.NO_PERMISSION(event.getPlayer());
                                        }
                                    }
                                    done();
                                }
                            }
                        };


                }
            }


    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event){
        if(event.getBlock().getState() instanceof Sign) {
            Sign sign = (Sign) event.getBlock().getState();
            //Get blockface of sign.
            if(sign.getBlockData() instanceof Directional) {

                BlockFace chestFace = ((Directional) sign.getBlockData()).getFacing().getOppositeFace();
                Block chest = sign.getBlock().getRelative(chestFace);

                //If block sign is placed on is a chest we can remove it.
                if(chest.getState() instanceof Chest) {

                    ChestLinkInfo info = Utils.getChestLinkInfo(sign,null);

                    if (info != null) { ;
                        Config.removeChest(info.getPlayer(), info.getGroup(), chest.getLocation());
                        ((Chest) chest.getState()).getInventory().clear();
                        Messages.CHEST_REMOVED(event.getPlayer(),info.getGroup(),info.getPlayer().getDisplayName());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChestPlace(BlockPlaceEvent event){
        if(event.getBlockPlaced().getState() instanceof Chest){
            new ChestLinkVerifier(event.getBlock()).check();
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestBreak(BlockBreakEvent event){
        if(event.getBlock().getState() instanceof Chest){
            InventoryStorage storage = Config.removeChest(event.getBlock().getLocation());
            if(storage != null){
                Messages.CHEST_REMOVED(event.getPlayer(),storage.getIdentifier(),storage.getOwner().getDisplayName());
            }
        }
    }

    private void setLine(Sign sign, SignChangeEvent signChangeEvent, int i, String s){
        sign.setLine(i,s);
        signChangeEvent.setLine(i,s);
    }

}
