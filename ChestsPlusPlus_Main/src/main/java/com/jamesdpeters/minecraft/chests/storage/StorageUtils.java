package com.jamesdpeters.minecraft.chests.storage;

import com.jamesdpeters.minecraft.chests.Values;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.storage.abstracts.AbstractStorage;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageInfo;
import com.jamesdpeters.minecraft.chests.storage.abstracts.StorageType;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public record StorageUtils<T extends StorageInfo<S>, S extends AbstractStorage>(StorageType<S> storageType) {

    public StorageInfo<S> getStorageInfo(Sign sign, String[] lines, UUID uuid) {
        if (lines != null) {
            if (lines.length >= 2 && lines[0].contains(storageType.getSignTag())) {
                String playerUUID = sign.getPersistentDataContainer().get(Values.Instance().playerUUID, PersistentDataType.STRING);
                String group = ChatColor.stripColor(StringUtils.substringBetween(lines[1], "[", "]"));
                if (playerUUID == null) {
                    if (uuid == null) return null;
                    playerUUID = uuid.toString();
                    if (lines[2] != null) {
                        OfflinePlayer owner = Config.getOfflinePlayer(lines[2]);
                        if (owner != null) {
                            AbstractStorage storage = storageType.getStorage(owner.getUniqueId(), group);
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && storage.hasPermission(player))
                                playerUUID = owner.getUniqueId().toString();
                        }
                    }
                }
                return new StorageInfo<>(playerUUID, group, storageType, sign);
            }
        }
        return null;
    }

    public StorageInfo<S> getStorageInfo(Sign sign, String[] lines) {
        return getStorageInfo(sign, lines, null);
    }

    public StorageInfo<S> getStorageInfo(Sign sign) {
        return getStorageInfo(sign, sign.getLines());
    }

    /**
     * Returns StorageInfo for a sign.
     *
     * @param location - Location of Storage to find.
     * @return @{@link StorageInfo}
     */
    public StorageInfo<S> getStorageInfo(Location location) {
        Block block = location.getBlock();
        BlockFace face = storageType.getStorageFacing(block);
        if (face == null) return null;
        Block sign = block.getRelative(face);

        if (sign.getBlockData() instanceof Directional directional) {
            //Check if the sign is attached to the given block.
            if (directional.getFacing() != face) return null;
            //If it is we can extract info from it.
            if (sign.getState() instanceof Sign s) {
                return getStorageInfo(s);
            }
        }
        return null;
    }

    /**
     * Checks if the block being placed against is valid.
     * Doesn't check if this block is already apart of the group.
     *
     * @param location - location of sign being placed.
     * @return true if valid.
     */
    public boolean isValidSignPosition(Location location) {
        Block block = location.getBlock();
        if (block.getBlockData() instanceof Directional sign) {
            BlockFace facing = sign.getFacing().getOppositeFace();
            Block toTest = block.getRelative(facing);

            //Check if block face is a valid place for a sign!
            if (!storageType.getValidBlockFaces(toTest).contains(sign.getFacing())) return false;

            //Return if block isn't valid
            if (!storageType.isValidBlockType(toTest)) return false;

            //Check if block placed against is already part of this group.
            StorageInfo<S> info = getStorageInfo(toTest.getLocation());
            return (info == null);
        }
        return false;
    }
}
