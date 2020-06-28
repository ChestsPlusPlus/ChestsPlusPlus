package com.jamesdpeters.minecraft.chests.containers;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class ChestLinkInfo {

    private String group;
    private OfflinePlayer player;
    private InventoryStorage storage;

    public ChestLinkInfo(String playerUUID, String group){
        this(UUID.fromString(playerUUID),group);
    }

    public ChestLinkInfo(UUID playerUUID, String group){
        this.group = group;
        this.storage = Config.getInventoryStorage(playerUUID,group);
        this.player = Bukkit.getOfflinePlayer(playerUUID);
    }

    public String getGroup() {
        return group;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    /**
     * Get the InventoryStorage for this Sign and check if the given location is apart of the system if not
     * add it.
     * @return @{@link InventoryStorage}
     */
    public InventoryStorage getStorage(Location location) {
        if(!storage.containsLocation(location)) storage.addLocation(location);
        return storage;
    }
}
