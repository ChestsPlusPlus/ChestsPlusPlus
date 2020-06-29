package com.jamesdpeters.minecraft.chests.storage;

import com.jamesdpeters.minecraft.chests.misc.Messages;
import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.misc.Values;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class AutoCraftingStorageType extends StorageType<AutoCraftingStorage> {

    public AutoCraftingStorageType(LinkedChest store) {
        super(store);
    }

    @Override
    public HashMap<String, HashMap<String, AutoCraftingStorage>> getStorageMap(LinkedChest store) {
        return store.autocraftingtables;
    }

    @Override
    public AutoCraftingStorage createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location) {
        return new AutoCraftingStorage(player, inventoryName, location, this);
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
    public void createStorage(Player player, Block block, String identifier) {
        if(isValidBlockType(block)){
            BlockFace facing = Utils.getBlockFace(player);
            if(facing != null) {
                if(Utils.isSideFace(facing)) {
                    Block toReplace = block.getRelative(facing);
                    StorageInfo info = getStorageUtils().getStorageInfo(block.getLocation());
                    if(info != null){
                        Messages.ALREADY_PART_OF_GROUP(player,"Crafting Table");
                        return;
                    }
                    placeSign(block, toReplace, facing, player, identifier, Values.AutoCraftTag);
                }
            }
        }
    }

    private static final BlockFace[] blockfaces = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

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
}
