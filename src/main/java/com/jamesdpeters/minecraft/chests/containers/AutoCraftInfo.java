package com.jamesdpeters.minecraft.chests.containers;

import com.jamesdpeters.minecraft.chests.serialize.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class AutoCraftInfo {

    private String group;
    private OfflinePlayer player;
    private AutoCraftingStorage storage;

    public AutoCraftInfo(String playerUUID, String group){
        this(UUID.fromString(playerUUID),group);
    }

    public AutoCraftInfo(UUID playerUUID, String group){
        this.group = group;
        this.storage = Config.getAutoCraftStorage(playerUUID,group);
        this.player = Bukkit.getOfflinePlayer(playerUUID);
    }

    public String getGroup() {
        return group;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    public AutoCraftingStorage getStorage() {
        return storage;
    }
}
