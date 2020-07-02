package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageInfo;
import com.jamesdpeters.minecraft.chests.misc.*;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.storage.abstracts.AbstractStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.persistence.PersistentDataType;

public class StorageListener implements Listener {

    @EventHandler
    public void playerInteract(BlockPlaceEvent event){
            if(event.getBlockPlaced().getState() instanceof Sign){
                if(Config.getStorageTypes().stream().anyMatch(storageType -> storageType.isValidBlockType(event.getBlockAgainst()))) {
                        new TempListener() {
                            @EventHandler
                            public void onSignChange(SignChangeEvent signChangeEvent) {
                                if (event.getBlockPlaced().getLocation().equals(signChangeEvent.getBlock().getLocation())) {
                                    Sign sign = (Sign) signChangeEvent.getBlock().getState();

                                    for (StorageType storageType : Config.getStorageTypes()) {
                                        if(storageType.hasPermissionToAdd(event.getPlayer())) {
                                                StorageInfo info = storageType.getStorageUtils().getStorageInfo(sign, signChangeEvent.getLines(), event.getPlayer().getUniqueId());
                                                if (info != null) {
                                                    Location signLocation = event.getBlockPlaced().getLocation();
                                                    if (storageType.getStorageUtils().isValidSignPosition(signLocation)) {
                                                        if(!storageType.add(event.getPlayer(), info.getGroup(), event.getBlockAgainst().getLocation(), info.getPlayer())){
                                                            sign.getBlock().breakNaturally();
                                                            done();
                                                            return;
                                                        }
                                                        storageType.getMessages().storageAdded(event.getPlayer(), signChangeEvent.getLine(1), info.getPlayer().getName());
                                                        signChange(sign,signChangeEvent,info.getPlayer(),event.getPlayer());
                                                    } else {
                                                        storageType.getMessages().invalidSignPlacement(event.getPlayer());
                                                    }
                                                }
                                        } else {
                                            Messages.NO_PERMISSION(event.getPlayer());
                                        }
                                    }
                                }
                                done();
                            }
                        };
                }
            }
    }

    private void signChange(Sign sign, SignChangeEvent signChangeEvent, OfflinePlayer addedPlayer, Player player){
        setLine(sign, signChangeEvent, 0, ChatColor.RED + ChatColor.stripColor(signChangeEvent.getLine(0)));
        setLine(sign, signChangeEvent, 1, ChatColor.GREEN + ChatColor.stripColor(signChangeEvent.getLine(1)));
        setLine(sign, signChangeEvent, 2, ChatColor.BOLD + ChatColor.stripColor(addedPlayer.getName()));
        sign.getPersistentDataContainer().set(Values.playerUUID, PersistentDataType.STRING, addedPlayer.getUniqueId().toString());
        sign.update();
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event){
        if(event.getBlock().getState() instanceof Sign) {
            Sign sign = (Sign) event.getBlock().getState();
            //Get blockface of sign.
            if(sign.getBlockData() instanceof Directional) {

                BlockFace chestFace = ((Directional) sign.getBlockData()).getFacing().getOppositeFace();
                Block block = sign.getBlock().getRelative(chestFace);

                Config.getStorageTypes().forEach(storageType -> {
                    if(storageType.isValidBlockType(block)){
                        StorageInfo info = storageType.getStorageUtils().getStorageInfo(sign,sign.getLines());
                        if(info != null){
                            storageType.removeBlock(info.getPlayer(), info.getGroup(), block.getLocation());
                            storageType.onSignRemoval(block);
                            storageType.getMessages().storageRemoved(event.getPlayer(), info.getGroup(), info.getPlayer().getName());
                        }
                    }
                });
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
        for (StorageType storageType : Config.getStorageTypes()) {
            if(storageType.isValidBlockType(event.getBlock())) {
                AbstractStorage storage = storageType.removeBlock(event.getBlock().getLocation());
                if (storage != null) {
                    storageType.getMessages().storageRemoved(event.getPlayer(), storage.getIdentifier(), storage.getOwner().getName());
                }
            }
        }
    }

    private void setLine(Sign sign, SignChangeEvent signChangeEvent, int i, String s){
        sign.setLine(i,s);
        signChangeEvent.setLine(i,s);
    }

}
