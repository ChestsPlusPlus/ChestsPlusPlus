package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.SyntheticMoveEvent;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.ServerLoadEvent;

/** Opening the recipe editor from a crafter, waking waiting crafters when something next to them changes, and retrying unresolved recipes. */
@RequiredArgsConstructor
public final class AutoCraftListener implements Listener {

    private final Services services;
    private final LinkService links;
    private final AutoCraftService autoCraft;

    @EventHandler(priority = EventPriority.HIGH)
    void onInteract(PlayerInteractEvent event) {
        Node node = links.claimNodeClick(event, GroupType.AUTOCRAFT);
        if (node != null && services.groups().byId(node.groupId()) instanceof AutoCraftGroup group) autoCraft.openEditor(event.getPlayer(), group);
    }

    /** Items arriving at an input, or leaving a full output hopper. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        if (event instanceof SyntheticMoveEvent) return;
        autoCraft.inventoryChanged(event.getDestination());
        autoCraft.inventoryChanged(event.getSource());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onClose(InventoryCloseEvent event) {
        autoCraft.inventoryChanged(event.getInventory());
    }

    /** A player putting items into an open chest by hand; the wake-up lands next tick, after the click has been applied. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onClick(InventoryClickEvent event) {
        autoCraft.inventoryChanged(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDrag(InventoryDragEvent event) {
        autoCraft.inventoryChanged(event.getView().getTopInventory());
    }

    /** E.g. the chest above or hopper below being placed while setting a crafter up. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        autoCraft.blockChanged(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        autoCraft.blockChanged(event.getBlock());
    }

    /** Last, so recipes other plugins register in their own handler for this event are already there. */
    @EventHandler(priority = EventPriority.MONITOR)
    void onServerLoad(ServerLoadEvent event) {
        autoCraft.resolveDeferred();
    }
}
