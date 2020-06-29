package com.jamesdpeters.minecraft.chests.storage;

import com.jamesdpeters.minecraft.chests.storage.AbstractStorage;
import com.jamesdpeters.minecraft.chests.storage.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.StorageType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class StorageInfo<T extends AbstractStorage> {

    private String group;
    private OfflinePlayer player;
    private T storage;

    public StorageInfo(String playerUUID, String group, StorageType<T> storageType){
        this(UUID.fromString(playerUUID),group, storageType);
    }

    public StorageInfo(UUID playerUUID, String group, StorageType<T> storageType){
        this.group = group;
        this.storage = storageType.getStorage(playerUUID,group);
        this.player = Bukkit.getOfflinePlayer(playerUUID);
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
     * @return @{@link AutoCraftingStorage}
     */
    public T getStorage(Location location) {
        if(!storage.containsLocation(location)) storage.addLocation(location);
        return storage;
    }
}
