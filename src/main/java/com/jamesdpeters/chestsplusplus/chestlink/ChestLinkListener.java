package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Opening ChestLinks by clicking a node, viewer open/close bookkeeping, and dirty-marking hopper transfers. */
public final class ChestLinkListener implements Listener {

    private final Services services;
    private final LinkService links;
    private final ChestLinkService chestLinks;

    public ChestLinkListener(Services services, LinkService links, ChestLinkService chestLinks) {
        this.services = services;
        this.links = links;
        this.chestLinks = chestLinks;
    }

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
