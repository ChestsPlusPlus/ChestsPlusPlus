package com.jamesdpeters.minecraft.chests.containers;

import com.jamesdpeters.minecraft.chests.Config;
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
        this(Bukkit.getOfflinePlayer(UUID.fromString(playerUUID)).getPlayer(),group);
    }

    public ChestLinkInfo(Player player, String group){
        this.group = group;
        this.storage = Config.getInventoryStorage(player,group);
        this.player = player;
        this.playerUUID = player.getUniqueId();
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
