package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkHolder;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.core.Holders;
import com.jamesdpeters.chestsplusplus.core.InventoryChanges;
import com.jamesdpeters.chestsplusplus.core.Items;
import com.jamesdpeters.chestsplusplus.core.PaperApi;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.ProtectionCheck;
import com.jamesdpeters.chestsplusplus.link.SyntheticMoveEvent;
import com.jamesdpeters.chestsplusplus.message.Message;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.jspecify.annotations.Nullable;

/** Hopper filter enforcement, the editor, and index upkeep. */
@RequiredArgsConstructor
public final class FilterListener implements Listener {

    /** Minimum ticks between manual stall-avoidance moves per hopper (vanilla hopper cooldown). */
    static final int STALL_COOLDOWN_TICKS = 8;

    private final Services services;
    private final FilterService filters;
    private final ProtectionCheck protection;
    private final ChestLinkService chestLinks;
    private final InventoryChanges changes;

    private boolean enabled() {
        return services.settings().features().hopperFilters();
    }

    /**
     * Hot path: one type check, one holder lookup, one hash lookup. A synthetic move gets the filter's answer but no stall avoidance, as
     * nothing is moving; a relayed one is skipped, since the real move it repeats is judged here too.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        if (destination.getType() != InventoryType.HOPPER || !enabled()) return;
        if (event instanceof SyntheticMoveEvent synthetic && synthetic.relayed()) return;
        // Hopper blocks only (minecart hoppers have an entity holder).
        if (!(Holders.of(destination) instanceof Hopper hopper)) return;
        CompiledFilter filter = filters.get(hopper.getWorld().getUID(), hopper.getX(), hopper.getY(), hopper.getZ());
        if (filter == null || filter.accepts(event.getItem())) return;
        event.setCancelled(true);
        if (!(event instanceof SyntheticMoveEvent)) avoidStall(event.getSource(), destination, filter, event.getItem().getAmount());
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
     * cooldown, with a single slot scan. Other plugins only saw the rejected item, so they are asked about this one first; a refusal
     * waits out the cooldown.
     */
    private void avoidStall(Inventory source, Inventory destination, CompiledFilter filter, int amount) {
        int now = services.plugin().getServer().getCurrentTick();
        if (!filter.manualMoveAllowed(now)) return;
        @Nullable ItemStack[] contents = PaperApi.contents(source);
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!Items.present(item) || !filter.accepts(item)) continue;
            ItemStack moving = item.asQuantity(Math.min(amount, item.getAmount()));
            if (!SyntheticMoveEvent.allows(source, moving, destination)) {
                filter.allowManualMoveAt(now + STALL_COOLDOWN_TICKS);
                return;
            }
            Map<Integer, ItemStack> leftover = destination.addItem(moving.clone());
            int moved = moving.getAmount() - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (moved <= 0) continue;
            item.setAmount(item.getAmount() - moved);
            source.setItem(slot, item.isEmpty() ? null : item);
            filter.allowManualMoveAt(now + STALL_COOLDOWN_TICKS);
            if (Holders.of(source) instanceof ChestLinkHolder holder) chestLinks.changed(holder.group());
            else changes.changed(source);
            changes.changed(destination);
            return;
        }
    }

    /** At MONITOR, like {@link LinkService#claimNodeClick}: the DENY cancels the event, so every protection plugin must have passed it first. */
    @EventHandler(priority = EventPriority.MONITOR)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || protection.isFiringSyntheticInteract()) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || block.getType() != Material.HOPPER || !player.isSneaking()) return;
        if (!player.getInventory().getItemInMainHand().isEmpty() || !enabled()) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Message refusal = editRefusal(player, block, true);
        if (refusal != null) services.send(player, refusal);
        else player.openInventory(new FilterEditorHolder(block, filters.read(block), services.messages(), filters, this::editRefusal).getInventory());
    }

    private @Nullable Message editRefusal(Player player, Block hopper) {
        return editRefusal(player, hopper, false);
    }

    /**
     * Why {@code player} may not edit {@code hopper}'s filters, or null if they may. The open editor asks again before every save. The
     * protection check runs last as it fires an event.
     *
     * @param protectionChecked true only for a real right-click on {@code hopper} that no plugin denied
     */
    private @Nullable Message editRefusal(Player player, Block hopper, boolean protectionChecked) {
        if (!enabled()) return Message.ERROR_FEATURE_DISABLED;
        if (!player.hasPermission(Permissions.FILTER)) return Message.ERROR_NO_PERMISSION;
        if (services.settings().isBlacklisted(hopper.getWorld().getName())) return Message.ERROR_WORLD_BLACKLISTED;
        if (!protectionChecked && !protection.passes(player, hopper, BlockFace.UP)) return Message.ERROR_PROTECTED;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.HOPPER) filters.removed(event.getBlock());
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
    }
}
