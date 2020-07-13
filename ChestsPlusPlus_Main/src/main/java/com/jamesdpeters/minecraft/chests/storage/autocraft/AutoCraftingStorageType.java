package com.jamesdpeters.minecraft.chests.storage.autocraft;

import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.misc.Values;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.ConfigStorage;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageInfo;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageMessages;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AutoCraftingStorageType extends StorageType<AutoCraftingStorage> {

    private static final List<BlockFace> blockfaces = Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    public AutoCraftingStorageType(ConfigStorage store) {
        super(store);
    }

    @Override
    public HashMap<String, HashMap<String, AutoCraftingStorage>> getStorageMap(ConfigStorage store) {
        return store.autocraftingtables;
    }

    @Override
    public AutoCraftingStorage createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location, Location sign) {
        return new AutoCraftingStorage(player, inventoryName, location, sign);
    }

    @Override
    public boolean isValidBlockType(Block block) {
        return (block.getType() == Material.CRAFTING_TABLE);
    }

    @Override
    public void onSignRemoval(Block block) {
        //Don't need to do anything with the Crafting Table.
    }

    @Override
    public String getSignTag() {
        return Values.AutoCraftTag;
    }

    @Override
    public boolean hasPermissionToAdd(Player player) {
        return player.hasPermission(Permissions.AUTOCRAFT_ADD);
    }

    @Override
    public void createStorage(Player player, OfflinePlayer owner, Block block, String identifier, boolean requireSign) {
        if(isValidBlockType(block)){
            BlockFace facing = Utils.getBlockFace(player);
            if(facing != null) {
                createStorageFacing(player, owner, block, identifier, facing, requireSign);
            }
        }
    }

    @Override
    public void createStorageFacing(Player player, OfflinePlayer owner, Block block, String identifier, BlockFace facing, boolean requireSign) {
        if(Utils.isSideFace(facing)) {
            Block toReplace = block.getRelative(facing);
            StorageInfo info = getStorageUtils().getStorageInfo(block.getLocation());
            if(info != null){
                Messages.ALREADY_PART_OF_GROUP(player,"Crafting Table");
                return;
            }
            placeSign(block, toReplace, facing, player, owner, identifier, Values.AutoCraftTag,requireSign);
        }
    }

    @Override
    public BlockFace onStoragePlacedBlockFace(Player player, Block placed) {
        return Utils.getNearestBlockFace(player,placed.getLocation());
    }

    @Override
    public BlockFace getStorageFacing(Block block) {
        for(BlockFace face : blockfaces){
            Block sign = block.getRelative(face);
            if(sign.getState() instanceof Sign) {
                StorageInfo<AutoCraftingStorage> info = Config.getAutoCraft().getStorageUtils().getStorageInfo((Sign) sign.getState());
                if (info != null) return face;
            }
        }
        return null;
    }

    @Override
    public List<BlockFace> getValidBlockFaces(Block block) {
        return blockfaces;
    }

    @Override
    public void validate(Block block) {
        //Doesn't do any validation.
    }

    @Override
    public StorageMessages getMessages() {
        return messages;
    }

    private static AutoCraftMessages messages = new AutoCraftMessages();

    private static class AutoCraftMessages extends StorageMessages {

        @Override
        public String getStorageName() {
            return "AutoCrafter";
        }

        @Override
        public void invalidID(Player target) {
            target.sendMessage(ChatColor.RED+"Invalid AutoCrafter ID! Must not contain a colon ':' unless you are referencing another players group that you are a member off");
            target.sendMessage(ChatColor.RED+"/autocraft add <owner>:<group>");
        }

        @Override
        public void listStorageGroups(Player target) {
            target.sendMessage(ChatColor.GREEN+""+ChatColor.BOLD+"List of your AutoCraft Stations:");
            for(AutoCraftingStorage storage : Config.getAutoCraft().getStorageMap(target.getUniqueId()).values()){
                if(storage != null){
                    target.sendMessage(ChatColor.GREEN+storage.getIdentifier()+ChatColor.WHITE);
                }
            }
        }

        @Override
        public void mustLookAtBlock(Player player) {
            player.sendMessage(ChatColor.RED+TAG+" You must be looking at the Crafting Table you want to AutoCraft with!");
        }

        @Override
        public void invalidSignPlacement(Player player) {
            player.sendMessage(ChatColor.GOLD+""+ChatColor.BOLD+TAG+" Invalid AutoCrafter - You must place a sign on any side of a Crafting Table, and it must not already by apart of a group!");
        }
    }
}
