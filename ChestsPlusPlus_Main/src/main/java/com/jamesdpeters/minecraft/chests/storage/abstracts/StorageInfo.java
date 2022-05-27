package com.jamesdpeters.minecraft.chests.storage.abstracts;

import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.UUID;

public class StorageInfo<T extends AbstractStorage> {

    private final String group;
    private final OfflinePlayer player;
    private T storage;

    public StorageInfo(String playerUUID, String group, StorageType<T> storageType, Sign sign) {
        this(UUID.fromString(playerUUID), group, storageType, sign);
    }

    public StorageInfo(UUID playerUUID, String group, StorageType<T> storageType, Sign sign) {
        this.group = group;
        this.player = Bukkit.getOfflinePlayer(playerUUID);
        this.storage = storageType.getStorage(playerUUID, group);
        if (storage == null) {
            if (sign.getBlockData() instanceof Directional directional) {
                BlockFace storageFace = directional.getFacing().getOppositeFace();
                Block storageBlock = sign.getBlock().getRelative(storageFace);
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null) {
                    boolean added = storageType.add(player, group, storageBlock.getLocation(), sign.getLocation(), this.player);
                    if (added) {
                        this.storage = storageType.getStorage(playerUUID, group);
//                        storageType.getMessages().foundUnlinkedStorage(player,group);
                    }
                }
            }
        }
    }

    public String getGroup() {
        return group;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    /**
     * Get the AutoCraftingStorage for this Sign and check if the given location is apart of the system if not
     * add it.
     *
     * @return @{@link AutoCraftingStorage}
     */
    public T getStorage(Location location) {
        if (storage == null) return null;
        if (!storage.containsLocation(location)) {
            storage.addLocation(location, storage.getSignLocation(location));
            Player player = storage.getOwner().getPlayer();
            if (player != null) storage.getStorageType().getMessages().foundUnlinkedStorage(player, getGroup());
        }
        return storage;
    }
}
