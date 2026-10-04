package com.jamesdpeters.chestsplusplus.ui.menu;

import com.jamesdpeters.chestsplusplus.core.Holders;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Dispatches menu clicks and handles "return to the menu you came from": when a player opens something from a menu
 * (e.g. a ChestLink), closing it reopens the menu. Tracked per viewer and cleared on quit (v2 leaked these).
 */
public final class MenuListener implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Runnable> returnTo = new HashMap<>();

    public MenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** After {@code player} next closes a non-menu inventory, run {@code reopen} (e.g. show the grid again). */
    public void returnTo(Player player, Runnable reopen) {
        returnTo.put(player.getUniqueId(), reopen);
    }

    public void forget(Player player) {
        returnTo.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    void onClick(InventoryClickEvent event) {
        if (!(Holders.of(event.getInventory()) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Menu.Button button = menu.button(event.getSlot());
        if (button != null) button.handler().onClick(player, event.getClick());
    }

    @EventHandler(priority = EventPriority.LOW)
    void onDrag(InventoryDragEvent event) {
        if (Holders.of(event.getInventory()) instanceof Menu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;
        if (Holders.of(event.getInventory()) instanceof Menu menu) {
            menu.onClose(player);
            return;
        }
        Runnable back = returnTo.remove(player.getUniqueId());
        if (back != null && event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) back.run();
            });
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        returnTo.remove(event.getPlayer().getUniqueId());
    }
}
