package com.jamesdpeters.minecraft.chests.players;

import com.jamesdpeters.minecraft.chests.reflection.craftbukkit.CraftPlayer;
import com.jamesdpeters.minecraft.chests.reflection.craftbukkit.CraftServer;
import com.jamesdpeters.minecraft.chests.reflection.craftbukkit.CraftWorld;
import com.jamesdpeters.minecraft.chests.reflection.minecraft.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.world.WorldSaveEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerStorage implements Listener {

    private static final HashMap<UUID, Player> offlinePlayers = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            var player = offlinePlayers.remove(event.getPlayer().getUniqueId());
            if (player != null) {
                player.saveData();
                event.getPlayer().loadData();
            }
        }
    }

    @EventHandler
    public void onSave(WorldSaveEvent event) {
        offlinePlayers.forEach((uuid, player) -> {
            player.saveData();
        });
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        Player player = (Player) event.getView().getPlayer();
        player.giveExp(10);

        Bukkit.broadcastMessage("Inventory type: "+player.getOpenInventory().getType());
    }

    public static void runMethodOnOfflinePlayer(OfflinePlayer offlinePlayer, World world, Consumer<Player> playerConsumer) {
        Player player = getPlayer(offlinePlayer, world);
        if (player != null) {
            playerConsumer.accept(player);
        }
    }

    private static Player getPlayer(OfflinePlayer offlinePlayer, World world) {
        Player player = offlinePlayer.getPlayer();
        if (player != null)
            return player;

        player = offlinePlayers.get(offlinePlayer.getUniqueId());
        if (player != null)
            return player;

        try {
            player = generateFakePlayer(offlinePlayer, world);
            offlinePlayers.put(offlinePlayer.getUniqueId(), player);
            return player;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Player generateFakePlayer(OfflinePlayer offlinePlayer, World world) throws NoSuchMethodException {
        var craftServer = new CraftServer(Bukkit.getServer());
        var minecraftServer = craftServer.getServer();
        var craftWorld = new CraftWorld(world);
        var worldServer = craftWorld.getWorldServer();

        var gameProfile = minecraftServer.getGameProfile(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        var entityPlayer = new EntityPlayer(minecraftServer, worldServer, gameProfile);
        minecraftServer.loadEntity(entityPlayer);

        var craftPlayer = new CraftPlayer(craftServer, entityPlayer);
        var player = craftPlayer.getOriginalObj();
        player.loadData();
        return player;
    }
}
