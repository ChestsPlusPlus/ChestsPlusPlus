package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

/** Opening ChestLinks by clicking a node, viewer open/close bookkeeping, and dirty-marking player edits and hopper transfers. */
@RequiredArgsConstructor
public final class ChestLinkListener implements Listener {

    private final Services services;
    private final LinkService links;
    private final ChestLinkService chestLinks;

    @EventHandler(priority = EventPriority.HIGH)
    void onInteract(PlayerInteractEvent event) {
        Node node = links.claimNodeClick(event, GroupType.CHESTLINK);
        if (node != null && services.groups().byId(node.groupId()) instanceof ChestLinkGroup group) chestLinks.open(event.getPlayer(), group, node);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onOpen(InventoryOpenEvent event) {
        if (Holders.of(event.getInventory()) instanceof ChestLinkHolder holder) {
            chestLinks.viewerOpened(holder.group(), event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onClose(InventoryCloseEvent event) {
        if (Holders.of(event.getInventory()) instanceof ChestLinkHolder holder) {
            chestLinks.viewerClosed(holder.group(), event.getPlayer());
        }
    }

    /**
     * Player edits mark an open ChestLink dirty, so a crash before close still saves them. Checks the top inventory, since a shift-click
     * into the ChestLink clicks the player's own inventory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onClick(InventoryClickEvent event) {
        edited(event.getView());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDrag(InventoryDragEvent event) {
        edited(event.getView());
    }

    private void edited(InventoryView view) {
        if (Holders.of(view.getTopInventory()) instanceof ChestLinkHolder holder) chestLinks.changed(holder.group());
    }

    /** Hopper transfers in or out of a ChestLink mark it dirty and refresh its display. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        if (Holders.of(event.getDestination()) instanceof ChestLinkHolder holder) chestLinks.changed(holder.group());
        if (Holders.of(event.getSource()) instanceof ChestLinkHolder holder) chestLinks.changed(holder.group());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        chestLinks.forgetViewer(event.getPlayer().getUniqueId());
    }
}
