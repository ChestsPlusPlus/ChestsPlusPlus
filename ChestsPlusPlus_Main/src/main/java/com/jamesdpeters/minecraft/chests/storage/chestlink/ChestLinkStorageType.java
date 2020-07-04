package com.jamesdpeters.minecraft.chests.storage.chestlink;

import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Values;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageMessages;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class ChestLinkStorageType extends StorageType<ChestLinkStorage> {

    public ChestLinkStorageType(ConfigStorage store) {
        super(store);
    }

    @Override
    public HashMap<String, HashMap<String, ChestLinkStorage>> getStorageMap(ConfigStorage store) {
        return store.chests;
    }

    @Override
    public ChestLinkStorage createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location) {
        return new ChestLinkStorage(player, inventoryName, location);
    }

    @Override
    public boolean isValidBlockType(Block block) {
        return (block.getState() instanceof Chest);
    }

    @Override
    public void onSignRemoval(Block block) {
        if(block.getState() instanceof Chest){
            ((Chest) block.getState()).getInventory().clear();
        }
    }

    @Override
    public boolean hasPermissionToAdd(Player player) {
        return player.hasPermission(Permissions.ADD);
    }

    @Override
    public void createStorage(Player player, Block block, String identifier) {
        if(block.getState() instanceof Chest){
            new ChestLinkVerifier(block).withDelay(0).check();
            if(block.getBlockData() instanceof Directional) {
                Directional chest = (Directional) block.getBlockData();
                BlockFace facing = chest.getFacing();
                Block toReplace = block.getRelative(facing);
                placeSign(block,toReplace,facing,player,identifier,Values.ChestLinkTag);
            }
        }
    }

    @Override
    public String getSignTag() {
        return Values.ChestLinkTag;
    }


    @Override
    public BlockFace getStorageFacing(Block block) {
        if(block.getBlockData() instanceof Directional) {
            Directional chest = (Directional) block.getBlockData();
            return chest.getFacing();
        }
        return null;
    }

    @Override
    public StorageMessages getMessages() {
        return messages;
    }

    private static ChestLinkMessages messages = new ChestLinkMessages();

    private static class ChestLinkMessages extends StorageMessages {

        @Override
        public String getStorageName() {
            return "ChestLink";
        }

        @Override
        public void invalidID(Player target) {
            target.sendMessage(ChatColor.RED+"Invalid ChestLink ID! Must not contain a colon ':' unless you are referencing another players group that you are a member off");
            target.sendMessage(ChatColor.RED+"/chestlink add <owner>:<group>");
        }

        @Override
        public void listStorageGroups(Player target) {
            target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+"List of your ChestLinks:");
            for(ChestLinkStorage storage : Config.getChestLink().getStorageMap(target.getUniqueId()).values()){
                if(storage != null){
                    target.sendMessage(ChatColor.GREEN+storage.getIdentifier()+ChatColor.WHITE+" - "+storage.getTotalItems()+" items");
                }
            }
        }

        @Override
        public void mustLookAtBlock(Player player) {
            player.sendMessage(ChatColor.RED+TAG+" You must be looking at the chest you want to ChestLink!");
        }

        @Override
        public void invalidSignPlacement(Player player) {
            player.sendMessage(ChatColor.GOLD+""+ChatColor.BOLD+TAG+" Invalid ChestLink - You must place a sign on the front of a chest / you should ensure there is space for a sign on front of the chest!");
        }
    }
}
