package com.jamesdpeters.chestsplusplus.core;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;

/** Display names for player UUIDs. A name the server hasn't cached is read from the player's data file, so avoid it in loops. */
public final class PlayerNames {

    private PlayerNames() {}

    /** The player's last known name, or the start of their UUID if the server has never seen them. */
    public static String of(UUID player) {
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
    }

    public static String join(Collection<UUID> players) {
        return players.stream().map(PlayerNames::of).collect(Collectors.joining(", "));
    }
}
