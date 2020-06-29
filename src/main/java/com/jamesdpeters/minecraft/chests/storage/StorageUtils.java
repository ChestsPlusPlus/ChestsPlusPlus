package com.jamesdpeters.minecraft.chests.storage;

import com.jamesdpeters.minecraft.chests.misc.Values;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class StorageUtils<T extends StorageInfo<S>, S extends AbstractStorage> {

//    private static StorageUtils<StorageInfo<AutoCraftingStorage>, AutoCraftingStorage> AUTO_CRAFT;
//    private static StorageUtils<StorageInfo<ChestLinkStorage>, ChestLinkStorage> CHEST_LINK;
//
//    public static StorageUtils<StorageInfo<AutoCraftingStorage>, AutoCraftingStorage> getAutoCraft() {
//        return AUTO_CRAFT;
//    }
//
//    public static StorageUtils<StorageInfo<ChestLinkStorage>, ChestLinkStorage> getChestLink() {
//        return CHEST_LINK;
//    }

    private StorageType<S> storageType;
    public StorageUtils(StorageType<S> storageType){
        this.storageType = storageType;
    }

//    public static void init(){
//        AUTO_CRAFT = new StorageUtils<>(Config.AUTO_CRAFT);
//        CHEST_LINK = new StorageUtils<>(Config.CHEST_LINK);
//    }

    public StorageInfo<S> getStorageInfo(Sign sign, String[] lines, UUID uuid){
        if(lines != null) {
            if (lines.length >= 2 && lines[0].contains(storageType.getSignTag())) {
                String playerUUID = sign.getPersistentDataContainer().get(Values.playerUUID, PersistentDataType.STRING);
                String group = ChatColor.stripColor(StringUtils.substringBetween(lines[1], "[", "]"));
                if(playerUUID == null){
                    playerUUID = uuid.toString();
                    if(lines[2] != null){
                        OfflinePlayer owner = Config.getOfflinePlayer(lines[2]);
                        if(owner != null){
                            AbstractStorage storage = storageType.getStorage(owner.getUniqueId(), group);
                            if(storage.hasPermission(Bukkit.getPlayer(uuid))) playerUUID = owner.getUniqueId().toString();
                        }
                    }
                }
                return new StorageInfo<S>(playerUUID, group, storageType);
            }
        }
        return null;
    }

    public StorageInfo<S> getStorageInfo(Sign sign, String[] lines){
        return getStorageInfo(sign, lines, null);
    }

    public StorageInfo<S> getStorageInfo(Sign sign){
        return getStorageInfo(sign, sign.getLines());
    }

    /**
     * Returns StorageInfo for a sign.
     * @param location - Location of Storage to find.
     * @return @{@link StorageInfo}
     */
    public StorageInfo<S> getStorageInfo(Location location){
        Block block = location.getBlock();
        BlockFace face = storageType.getStorageFacing(block);
        if(face == null) return null;
        Block sign = block.getRelative(face);

        if(sign.getBlockData() instanceof Directional){
            //Check if the sign is attached to the given block.
            Directional directional = (Directional) sign.getBlockData();
            if(directional.getFacing() != face) return null;
            //If it is we can extract info from it.
            if (sign.getState() instanceof Sign) {
                Sign s = (Sign) sign.getState();
                return getStorageInfo(s);
            }
        }
        return null;
    }

    /**
     * Checks if the block being placed against is valid.
     * Doesn't check if this block is already apart of the group.
     * @param location - location of sign being placed.
     * @return true if valid.
     */
    public boolean isValidSignPosition(Location location){
        Block block = location.getBlock();
        if(block.getBlockData() instanceof Directional){
            Directional sign = (Directional) block.getBlockData();
            BlockFace facing = sign.getFacing().getOppositeFace();
            Block toTest = block.getRelative(facing);

            //Return if block isn't valid
            return storageType.isValidBlockType(toTest);

//            //Check if block placed against is already part of this group.
//            if(block.getState() instanceof Sign) {
//                StorageInfo info = getStorageInfo((Sign) block.getState());
//                return (info == null);
//            }
        }
        return false;
    }
}
