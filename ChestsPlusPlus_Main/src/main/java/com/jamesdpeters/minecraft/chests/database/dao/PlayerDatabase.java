package com.jamesdpeters.minecraft.chests.database.dao;

import com.jamesdpeters.minecraft.chests.database.entities.CppPlayer;
import com.jamesdpeters.minecraft.chests.misc.BukkitFuture;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerDatabase extends Database<CppPlayer> {

    public PlayerDatabase() {
        super(CppPlayer.class);
    }

    public CompletableFuture<CppPlayer> findPlayer(UUID uuid) {
        return BukkitFuture.supplyAsync(() -> findEntity(uuid).orElseGet(() -> new CppPlayer(uuid)));
    }

    public CompletableFuture<CppPlayer> findPlayer(OfflinePlayer player) {
        return findPlayer(player.getUniqueId());
    }
}
