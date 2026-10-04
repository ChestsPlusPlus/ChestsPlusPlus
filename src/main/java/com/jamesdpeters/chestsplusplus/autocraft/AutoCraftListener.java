package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Opening the recipe editor from a crafter, and backoff resets when inputs change. */
public final class AutoCraftListener implements Listener {

    private final Services services;
    private final LinkService links;
    private final AutoCraftService autoCraft;

    public AutoCraftListener(Services services, LinkService links, AutoCraftService autoCraft) {
        this.services = services;
        this.links = links;
        this.autoCraft = autoCraft;
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onInteract(PlayerInteractEvent event) {
        Node node = links.claimNodeClick(event, GroupType.AUTOCRAFT);
        if (node != null && services.groups().byId(node.groupId()) instanceof AutoCraftGroup group) autoCraft.openEditor(event.getPlayer(), group);
    }

    /** Items arriving next to a crafter end its backoff. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        if (autoCraft.noBackoff()) return;
        Location at = event.getDestination().getLocation();
        if (at != null && at.getWorld() != null) autoCraft.inputChanged(at.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onClose(InventoryCloseEvent event) {
        if (autoCraft.noBackoff()) return;
        Location at = event.getInventory().getLocation();
        if (at != null && at.getWorld() != null) autoCraft.inputChanged(at.getBlock());
    }
}
