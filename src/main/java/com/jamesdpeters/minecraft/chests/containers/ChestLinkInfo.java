package com.jamesdpeters.minecraft.chests.containers;

import com.jamesdpeters.minecraft.chests.misc.Config;
import com.jamesdpeters.minecraft.chests.serialize.InventoryStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ChestLinkInfo {

    private String group;
    private UUID playerUUID;
    private Player player;
    private InventoryStorage storage;

    public ChestLinkInfo(String playerUUID, String group){
        this(UUID.fromString(playerUUID),group);
    }

    public ChestLinkInfo(UUID playerUUID, String group){
        this.group = group;
        this.storage = Config.getInventoryStorage(playerUUID,group);
        this.player = Bukkit.getOfflinePlayer(playerUUID).getPlayer();
        this.playerUUID = playerUUID;
    }

    public String getGroup() {
        return group;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Player getPlayer() {
        return player;
    }

    public InventoryStorage getStorage() {
        return storage;
    }
}
