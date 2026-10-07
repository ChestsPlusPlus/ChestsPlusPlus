package com.jamesdpeters.chestsplusplus.ui.menu;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.core.Holders;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Dispatches menu clicks and handles "return to the menu you came from": when a player opens something from a menu
 * (e.g. a ChestLink), closing it reopens the menu. Tracked per viewer and cleared on quit.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class MenuListener implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Runnable> returnTo = new HashMap<>();

    /** After {@code player} next closes a non-menu inventory, run {@code reopen} (e.g. show the grid again). */
    public void returnTo(Player player, Runnable reopen) {
        returnTo.put(player.getUniqueId(), reopen);
    }

    public void forget(Player player) {
        returnTo.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    void onClick(InventoryClickEvent event) {
        switch (Holders.of(event.getInventory())) {
            case Menu menu -> onMenuClick(event, menu);
            case GhostEditor editor -> onEditorClick(event, editor);
            case null, default -> {}
        }
    }

    private void onMenuClick(InventoryClickEvent event, Menu menu) {
        log.info("Menu clicked: {}", event.getClick());
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Menu.Button button = menu.button(event.getSlot());
        if (button != null) button.handler().onClick(player, event.getClick());
    }

    /**
     * Clicks in the player's own inventory work as normal, except shift-clicks, which would move items into the editor, and double-clicks,
     * whose "collect to cursor" also takes matching stacks out of the editor.
     */
    private void onEditorClick(InventoryClickEvent event, GhostEditor editor) {
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) editor.onClick(player, event.getSlot(), event.getCursor(), event.getClick());
    }

    @EventHandler(priority = EventPriority.LOW)
    void onDrag(InventoryDragEvent event) {
        switch (Holders.of(event.getInventory())) {
            case Menu _ -> event.setCancelled(true);
            case GhostEditor editor -> onEditorDrag(event, editor);
            case null, default -> {}
        }
    }

    /** A drag that touches the editor never moves real items; the editor gets the slots it covered instead. Raw slots below the top size are the editor's own. */
    private void onEditorDrag(InventoryDragEvent event, GhostEditor editor) {
        int topSize = event.getView().getTopInventory().getSize();
        List<Integer> slots = event.getRawSlots().stream().filter(slot -> slot < topSize).sorted().toList();
        if (slots.isEmpty()) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) editor.onDrag(player, slots, event.getOldCursor());
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
