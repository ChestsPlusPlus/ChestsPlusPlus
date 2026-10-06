package com.jamesdpeters.chestsplusplus.core;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/** Small helpers for hot-path lookups that must not snapshot block states. */
public final class Holders {

    private Holders() {}

    /**
     * {@code inventory.getHolder(false)} (no block-state snapshot). Falls back to {@code getHolder()} where the
     * non-snapshot variant is unavailable (MockBukkit); on Paper the fallback never runs.
     */
    public static @Nullable InventoryHolder of(Inventory inventory) {
        try {
            return inventory.getHolder(false);
        } catch (RuntimeException unsupported) {
            return inventory.getHolder();
        }
    }

    /** The block's own container inventory (no block-state snapshot), or null when it isn't a container. */
    public static @Nullable Inventory containerAt(Block block) {
        return block.getState(false) instanceof Container container ? container.getInventory() : null;
    }

    /** The horizontal direction an entity is looking towards, from its yaw. */
    public static BlockFace facing(Entity entity) {
        return facing(entity.getLocation().getYaw());
    }

    /** Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east. */
    public static BlockFace facing(float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (quarter) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
