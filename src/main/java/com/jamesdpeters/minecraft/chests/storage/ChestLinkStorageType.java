package com.jamesdpeters.minecraft.chests.storage;

import com.jamesdpeters.minecraft.chests.misc.Permissions;
import com.jamesdpeters.minecraft.chests.misc.Values;
import com.jamesdpeters.minecraft.chests.runnables.ChestLinkVerifier;
import com.jamesdpeters.minecraft.chests.serialize.LinkedChest;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class ChestLinkStorageType extends StorageType<ChestLinkStorage> {

    public ChestLinkStorageType(LinkedChest store) {
        super(store);
    }

    @Override
    public HashMap<String, HashMap<String, ChestLinkStorage>> getStorageMap(LinkedChest store) {
        return store.chests;
    }

    @Override
    public ChestLinkStorage createNewStorageInstance(OfflinePlayer player, String inventoryName, Location location) {
        return new ChestLinkStorage(player, inventoryName, location, this);
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
}
