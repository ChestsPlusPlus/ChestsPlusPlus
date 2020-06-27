package com.jamesdpeters.minecraft.chests.listeners;

import com.jamesdpeters.minecraft.chests.containers.AutoCraftInfo;
import com.jamesdpeters.minecraft.chests.containers.ChestLinkInfo;
import com.jamesdpeters.minecraft.chests.misc.*;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

public class ChestLinkListener implements Listener {

    @EventHandler
    public void playerInteract(BlockPlaceEvent event){
            if(event.getBlockPlaced().getState() instanceof Sign){
                if(event.getBlockAgainst().getState() instanceof Chest || event.getBlockAgainst().getType() == Material.CRAFTING_TABLE ) {
                        new TempListener() {
                            @EventHandler
                            public void onSignChange(SignChangeEvent signChangeEvent) {
                                if (event.getBlockPlaced().getLocation().equals(signChangeEvent.getBlock().getLocation())) {
                                    Sign sign = (Sign) signChangeEvent.getBlock().getState();
                                    ChestLinkInfo chestLinkInfo = Utils.getChestLinkInfo(sign, signChangeEvent.getLines(),event.getPlayer().getUniqueId());
                                    if (chestLinkInfo != null) {
                                        if(event.getPlayer().hasPermission(Permissions.ADD)) {
                                            if (Utils.isValidSignPosition(event.getBlockAgainst().getLocation())) {
                                                if(!Config.addChest(event.getPlayer(), chestLinkInfo.getGroup(), event.getBlockAgainst().getLocation(),chestLinkInfo.getPlayer())){
                                                    sign.getBlock().breakNaturally();
                                                    done();
                                                    return;
                                                }
                                                Messages.CHEST_ADDED(event.getPlayer(), signChangeEvent.getLine(1), chestLinkInfo.getPlayer().getName());
                                                signChange(sign,signChangeEvent,chestLinkInfo.getPlayer(),event.getPlayer());
                                            } else {
                                                Messages.SIGN_FRONT_OF_CHEST(event.getPlayer());
                                            }
                                        } else {
                                            Messages.NO_PERMISSION(event.getPlayer());
                                        }
                                        done();
                                        return;
                                    }
                                    AutoCraftInfo autoCraftInfo = Utils.getAutoCraftInfo(sign, signChangeEvent.getLines(),event.getPlayer().getUniqueId());
                                    if (autoCraftInfo != null) {
                                        if(event.getPlayer().hasPermission(Permissions.ADD)) {
                                            if (Utils.isValidAutoCraftSignPosition(event.getBlockPlaced().getLocation())) {
                                                if(!Config.addAutoCraft(event.getPlayer(), autoCraftInfo.getGroup(), event.getBlockAgainst().getLocation(),autoCraftInfo.getPlayer())){
                                                    sign.getBlock().breakNaturally();
                                                    done();
                                                    return;
                                                }
                                                Messages.AUTOCRAFT_ADDED(event.getPlayer(), signChangeEvent.getLine(1), autoCraftInfo.getPlayer().getName());
                                                signChange(sign,signChangeEvent,autoCraftInfo.getPlayer(),event.getPlayer());
                                            } else {
                                                Messages.INVALID_AUTOCRAFT_SIGN(event.getPlayer());
                                            }
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

                //If block sign is placed on is a chest we can remove it.
                if(block.getState() instanceof Chest) {
                    ChestLinkInfo info = Utils.getChestLinkInfo(sign,sign.getLines());
                    if (info != null) {
                        Config.removeChest(info.getPlayer(), info.getGroup(), block.getLocation());
                        ((Chest) block.getState()).getInventory().clear();
                        Messages.CHEST_REMOVED(event.getPlayer(),info.getGroup(),info.getPlayer().getName());
                    }
                }

                //If block sign is placed on is a crafting table we can remove it.
                if(block.getType() == Material.CRAFTING_TABLE) {
                    AutoCraftInfo info = Utils.getAutoCraftInfo(sign,sign.getLines());
                    if (info != null) {
                        Config.removeAutoCraft(info.getPlayer(), info.getGroup(), block.getLocation());
                        Messages.AUTOCRAFT_REMOVED(event.getPlayer(),info.getGroup(),info.getPlayer().getName());
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
                Messages.CHEST_REMOVED(event.getPlayer(),storage.getIdentifier(),storage.getOwner().getName());
            }
        }
        if(event.getBlock().getType() == Material.CRAFTING_TABLE){
            AutoCraftingStorage storage = Config.removeAutoCraft(event.getBlock().getLocation());
            if(storage != null){
                Messages.AUTOCRAFT_REMOVED(event.getPlayer(),storage.getIdentifier(),storage.getOwner().getName());
            }
        }
    }

    private void setLine(Sign sign, SignChangeEvent signChangeEvent, int i, String s){
        sign.setLine(i,s);
        signChangeEvent.setLine(i,s);
    }

}
