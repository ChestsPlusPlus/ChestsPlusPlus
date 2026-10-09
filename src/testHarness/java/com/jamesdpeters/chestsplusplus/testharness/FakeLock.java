package com.jamesdpeters.chestsplusplus.testharness;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Stands in for a container-lock plugin (Bolt, LWC, BlockLocker): a locked block's container refuses every hopper move in or out, and
 * copper golems may not use it. Like those plugins it finds the block through the inventory's holder, so an inventory with no block
 * holder looks unprotected.
 */
final class FakeLock implements Listener {

    private final Map<Block, Integer> locked = new HashMap<>();

    void lock(Block block) {
        locked.putIfAbsent(block, 0);
    }

    int golemRefusals(Block block) {
        return locked.getOrDefault(block, 0);
    }

    void clear() {
        locked.clear();
    }

    @EventHandler
    void onMove(InventoryMoveItemEvent event) {
        if (isLocked(event.getSource()) || isLocked(event.getDestination())) event.setCancelled(true);
    }

    /**
     * At LOW, like a lock plugin at NORMAL that loads before ChestsPlusPlus: same-priority listeners run in registration order, and the
     * harness loads after ChestsPlusPlus.
     */
    @EventHandler(priority = EventPriority.LOW)
    void onGolemTarget(ItemTransportingEntityValidateTargetEvent event) {
        Block block = event.getBlock();
        if (!locked.containsKey(block)) return;
        locked.merge(block, 1, Integer::sum);
        event.setAllowed(false);
    }

    private boolean isLocked(Inventory inventory) {
        @Nullable Block block = switch (inventory.getHolder(false)) {
            case BlockInventoryHolder holder -> holder.getBlock();
            case DoubleChest chest -> chest.getLocation().getBlock();
            case null, default -> null;
        };
        return block != null && locked.containsKey(block);
    }
}
