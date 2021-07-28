package com.jamesdpeters.minecraft.chests.database.dao;

import com.jamesdpeters.minecraft.chests.database.entities.Player;
import com.jamesdpeters.minecraft.database.hibernate.Database;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class PlayerDatabase extends Database<Player> {

    public PlayerDatabase() {
        super(Player.class);
    }

    public Player findPlayer(UUID uuid) {
        return findEntity(uuid).orElseGet(() -> new Player(uuid));
    }

    public Player findPlayer(OfflinePlayer player) {
        return findPlayer(player.getUniqueId());
    }
}
