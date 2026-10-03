package com.jamesdpeters.chestsplusplus.chestlink;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.Node;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || links.isFiringSyntheticInteract()) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Node node = services.nodes()
                .get(block.getWorld().getUID(), BlockPos.packed(block.getX(), block.getY(), block.getZ()));
        if (node == null || !(services.groups().byId(node.groupId()) instanceof ChestLinkGroup group)) return;
        Player player = event.getPlayer();
        // Sneaking with an item places blocks against the chest, as in vanilla.
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (services.settings().isBlacklisted(block.getWorld().getName())) {
            services.messages().send(player, Message.ERROR_WORLD_BLACKLISTED);
            return;
        }
        if (!player.hasPermission(Permissions.CHESTLINK_OPEN)) {
            services.messages().send(player, Message.ERROR_NO_PERMISSION);
            return;
        }
        if (!services.access().canAccess(player.getUniqueId(), player, group)) {
            services.messages().send(player, Message.ERROR_NO_ACCESS, Messages.text("group", group.name()));
            return;
        }
        chestLinks.open(player, group, node);
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

    /** Hopper transfers in or out of a ChestLink mark it dirty and refresh its display (plan §4.3). */
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
