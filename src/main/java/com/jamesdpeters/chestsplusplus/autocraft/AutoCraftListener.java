package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.Node;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Opening the recipe editor from a crafter, editor clicks, and backoff resets when inputs change. */
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || links.isFiringSyntheticInteract()) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Node node = services.nodes().at(block);
        if (node == null || !(services.groups().byId(node.groupId()) instanceof AutoCraftGroup group)) return;
        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (services.settings().isBlacklisted(block.getWorld().getName())) {
            services.messages().send(player, Message.ERROR_WORLD_BLACKLISTED);
            return;
        }
        if (!player.hasPermission(Permissions.AUTOCRAFT_OPEN)) {
            services.messages().send(player, Message.ERROR_NO_PERMISSION);
            return;
        }
        if (!services.access().canAccess(player.getUniqueId(), player, group)) {
            services.messages().send(player, Message.ERROR_NO_ACCESS, Messages.text("group", group.name()));
            return;
        }
        autoCraft.openEditor(player, group);
    }

    @EventHandler(priority = EventPriority.LOW)
    void onEditorClick(InventoryClickEvent event) {
        if (!(Holders.of(event.getInventory()) instanceof RecipeEditorHolder editor)) return;
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        if (!top) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Viewing is allowed for anyone with access; changing the recipe is for managers only.
        if (!services.access().canManage(player.getUniqueId(), player, editor.group())) {
            services.messages().send(player, Message.ERROR_NOT_OWNER, Messages.text("group", editor.group().name()));
            return;
        }
        ItemStack[] matrix = editor.click(event.getSlot(), event.getCursor());
        if (matrix == null) return;
        ItemStack result = autoCraft.setMatrix(editor.group(), matrix, player);
        editor.render();
        if (result != null) {
            services.messages().send(player, Message.AUTOCRAFT_RECIPE_SET, Messages.text("group", editor.group().name()),
                    Messages.text("item", result.getType().getKey().getKey().replace('_', ' ')));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    void onEditorDrag(InventoryDragEvent event) {
        if (Holders.of(event.getInventory()) instanceof RecipeEditorHolder) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
        }
    }

    /** Items arriving next to a crafter end its backoff (plan §5.9). */
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
