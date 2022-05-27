package com.jamesdpeters.minecraft.chests.storage.chestlink;

import com.jamesdpeters.minecraft.chests.Values;
import com.jamesdpeters.minecraft.chests.lang.Message;
import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageMessages;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ChestLinkStorageType extends StorageType<ChestLinkStorage> {

    public ChestLinkStorageType(ConfigStorage store) {
        super(store);
    }

    @Override
    public HashMap<String, HashMap<String, ChestLinkStorage>> getStorageMap(ConfigStorage store) {
        return store.chests;
    }

    @Override
    public ChestLinkStorage createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location, Location signLocation) {
        return new ChestLinkStorage(player, inventoryName, location, signLocation);
    }

    @Override
    public boolean isValidBlockType(Block block) {
        return (block.getState() instanceof Chest) || (block.getState() instanceof Barrel);
    }

    @Override
    public void onSignRemoval(Block block) {
        if (block.getState() instanceof Container) {
            ((Container) block.getState()).getInventory().clear();
        }
    }

    @Override
    public boolean hasPermissionToAdd(Player player) {
        return player.hasPermission(Permissions.ADD) && !Utils.isBlacklistedWorld(player.getWorld());
    }

    @Override
    public void createStorage(Player player, OfflinePlayer owner, Block block, String identifier, boolean requireSign) {
        if (block.getState() instanceof Chest) {
            new ChestLinkVerifier(block).withDelay(0).check();
        }
        createStorageForBlock(player, owner, block, identifier, requireSign);
    }

    private void createStorageForBlock(Player player, OfflinePlayer owner, Block block, String identifier, boolean requireSign) {
        if (block.getBlockData() instanceof Directional) {
            Directional chest = (Directional) block.getBlockData();
            BlockFace facing = chest.getFacing();
            Block toReplace = block.getRelative(facing);
            placeSign(block, toReplace, facing, player, owner, identifier, Values.ChestLinkTag, requireSign);
        }
    }

    @Override
    public void createStorageFacing(Player player, OfflinePlayer owner, Block block, String identifier, BlockFace facing, boolean requireSign) {
        //Chests already get placed facing in the correct direction.
        createStorage(player, owner, block, identifier, requireSign);
    }

    @Override
    public BlockFace onStoragePlacedBlockFace(Player player, Block placed) {
        if (placed.getBlockData() instanceof Directional) {
            return ((Directional) placed.getBlockData()).getFacing();
        }
        return null;
    }

    @Override
    public String getSignTag() {
        return Values.Instance().ChestLinkTag;
    }


    @Override
    public BlockFace getStorageFacing(Block block) {
        if (block.getBlockData() instanceof Directional) {
            Directional chest = (Directional) block.getBlockData();
            return chest.getFacing();
        }
        return null;
    }

    @Override
    public List<BlockFace> getValidBlockFaces(Block block) {
        return Collections.singletonList(getStorageFacing(block));
    }

    @Override
    public void validate(Block block) {
        if (block.getState() instanceof Chest) new ChestLinkVerifier(block).withDelay(0).check();
    }

    @Override
    public StorageMessages getMessages() {
        return messages;
    }

    @Override
    public void onBlockRightClick(PlayerInteractEvent event) {
            // ChestLink Check
            if (event.getClickedBlock() != null && isValidBlockType(event.getClickedBlock())) {
                Location location = event.getClickedBlock().getLocation();
                ChestLinkStorage storage = getStorage(location);
                if (storage != null) {
                    event.setCancelled(true);
                    if (event.getPlayer().hasPermission(Permissions.OPEN) && storage.hasPermission(event.getPlayer())) {
                        storage.getInventory().getViewers().remove(event.getPlayer());
                        Utils.openChestInventory(event.getPlayer(), storage, storage.getLocationInfo(location));
                    } else {
                        Messages.NO_PERMISSION(event.getPlayer());
                    }
                }
            }
    }

    private static final ChestLinkMessages messages = new ChestLinkMessages();

    private static class ChestLinkMessages extends StorageMessages {

        @Override
        public String getStorageName() {
            return "ChestLink";
        }

        @Override
        public void invalidID(Player target) {
            target.sendMessage(ChatColor.RED + Message.INVALID_ID.getString(getStorageName()));
            target.sendMessage(ChatColor.RED + "/chestlink add <owner>:<group>");
        }

        @Override
        public void listStorageGroups(Player target) {
            target.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + Message.LIST_OF_CHESTLINK);
            for (ChestLinkStorage storage : Config.getChestLink().getStorageMap(target.getUniqueId()).values()) {
                if (storage != null) {
                    target.sendMessage(ChatColor.GREEN + storage.getIdentifier() + ChatColor.WHITE + " - " + storage.getTotalItems() + " items");
                }
            }
        }

        @Override
        public void mustLookAtBlock(Player player) {
            player.sendMessage(ChatColor.RED + TAG + " " + Message.MUST_LOOK_AT_CHEST);
        }

        @Override
        public void invalidSignPlacement(Player player) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + TAG + " " + Message.INVALID_CHESTLINK);
        }
    }
}
