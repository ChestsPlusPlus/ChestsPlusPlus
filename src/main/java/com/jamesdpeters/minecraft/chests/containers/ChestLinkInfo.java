package com.jamesdpeters.minecraft.chests.containers;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
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

    public InventoryStorage getStorage() {
        return storage;
    }
}
