package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.message.Message;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Hopper filter enforcement, the editor, and index upkeep. */
@RequiredArgsConstructor
public final class FilterListener implements Listener {

    /** Minimum ticks between manual stall-avoidance moves per hopper (vanilla hopper cooldown). */
    static final int STALL_COOLDOWN_TICKS = 8;

    private final Services services;
    private final FilterService filters;
    private final LinkService links;
    private final ChestLinkService chestLinks;
    private final Map<BlockPos, Integer> lastManualMove = new HashMap<>();

    private boolean enabled() {
        return services.settings().features().hopperFilters();
    }

    /** Hot path: one type check, one holder lookup, one hash lookup. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        if (destination.getType() != InventoryType.HOPPER || !enabled()) return;
        // Hopper blocks only (minecart hoppers have an entity holder).
        if (!(Holders.of(destination) instanceof Hopper hopper)) return;
        CompiledFilter filter = filters.get(hopper.getWorld().getUID(), hopper.getX(), hopper.getY(), hopper.getZ());
        if (filter == null || filter.accepts(event.getItem())) return;
        event.setCancelled(true);
        avoidStall(event.getSource(), destination, filter, event.getItem().getAmount(), BlockPos.of(hopper.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPickup(InventoryPickupItemEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.HOPPER || !enabled()) return;
        if (!(Holders.of(inventory) instanceof Hopper hopper)) return;
        CompiledFilter filter = filters.get(hopper.getWorld().getUID(), hopper.getX(), hopper.getY(), hopper.getZ());
        if (filter != null && !filter.accepts(event.getItem().getItemStack())) event.setCancelled(true);
    }

    /**
     * A hopper still stalls when the first slot of its source is rejected. Move the first acceptable stack instead, once per hopper
     * cooldown, with a single slot scan and no event re-entry.
     */
    private void avoidStall(Inventory source, Inventory destination, CompiledFilter filter, int amount, BlockPos pos) {
        int now = services.plugin().getServer().getCurrentTick();
        Integer last = lastManualMove.get(pos);
        if (last != null && now - last < STALL_COOLDOWN_TICKS) return;
        ItemStack[] contents = source.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.isEmpty() || !filter.accepts(item)) continue;
            ItemStack moving = item.asQuantity(Math.min(amount, item.getAmount()));
            Map<Integer, ItemStack> leftover = destination.addItem(moving.clone());
            int moved = moving.getAmount() - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (moved <= 0) continue;
            item.setAmount(item.getAmount() - moved);
            source.setItem(slot, item.isEmpty() ? null : item);
            lastManualMove.put(pos, now);
            if (Holders.of(source) instanceof ChestLinkHolder holder) chestLinks.changed(holder.group());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || links.isFiringSyntheticInteract()) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || block.getType() != Material.HOPPER || !player.isSneaking()) return;
        if (!player.getInventory().getItemInMainHand().isEmpty() || !enabled()) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Message refusal = !player.hasPermission(Permissions.FILTER)
                ? Message.ERROR_NO_PERMISSION
                : services.settings().isBlacklisted(block.getWorld().getName())
                        ? Message.ERROR_WORLD_BLACKLISTED
                        : null;
        if (refusal != null) services.send(player, refusal);
        else player.openInventory(new FilterEditorHolder(block, filters.read(block), services.messages(), filters).getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) {
            filters.removed(event.getBlock());
            lastManualMove.remove(BlockPos.of(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().stream().filter(b -> b.getType() == Material.HOPPER).forEach(filters::removed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().stream().filter(b -> b.getType() == Material.HOPPER).forEach(filters::removed);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChunkLoad(ChunkLoadEvent event) {
        filters.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChunkUnload(ChunkUnloadEvent event) {
        filters.chunkUnloaded(event.getChunk());
        lastManualMove.keySet().removeIf(pos -> pos.chunkX() == event.getChunk().getX() && pos.chunkZ() == event.getChunk().getZ()
                && pos.world().equals(event.getWorld().getUID()));
    }
}
