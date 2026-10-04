package com.jamesdpeters.chestsplusplus.display;

import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Pure layout maths for node and filter displays: where, relative to the block's minimum corner, a display sits on a
 * face, and which yaw makes it face outward. Spike S4 picks the constants; see the plan (§13, S4).
 */
public final class DisplayLayout {

    /** Placement of one display: offset from the block's minimum corner, and entity yaw in degrees. */
    public record Placement(double x, double y, double z, float yaw) {}

    /** Which model the display sits on. Distances are from the block centre, in blocks. */
    public enum Surface {
        /** Chests: the body's front is 1px inside the block; the latch sticks out 1px to the block boundary. */
        CHEST(7.0 / 16, 8.0 / 16),
        /** Barrels, crafting tables: full cubes. */
        FULL_BLOCK(0.5, 0.5),
        /** Hopper bowl sides (filters), upper part of the block. */
        HOPPER_SIDE(0.5, 0.5);

        /** The model's front face. */
        private final double front;
        /** The furthest-forward part of the model (the chest latch). */
        private final double protrusion;

        Surface(double front, double protrusion) {
            this.front = front;
            this.protrusion = protrusion;
        }
    }

    /** How a node's item renders at {@link #NODE_ITEM_SCALE}, which decides where it can sit. */
    public enum Shape {
        /** Rendered as a 3D model (cubes, stairs, chests...): centred on the front face, so half of it sticks out. */
        BLOCK,
        /** Rendered as a flat 1px sheet (most items): placed in front of anything protruding, like the chest latch. */
        FLAT
    }

    /** Gap between the face and the display to avoid z-fighting. */
    static final double FACE_GAP = 0.02;

    /**
     * Added to the facing yaw. S4 row A (0) vs row B (180): pending the in-game check, row A is assumed. Flip here if
     * displays turn out to face into the block.
     */
    static final float ITEM_YAW_OFFSET = 0f;

    /**
     * Uniform scale of node item displays. Uniform (not flattened) so they render in 3D like an item frame: blocks as
     * small cubes and items with their pixel thickness.
     */
    public static final float NODE_ITEM_SCALE = 0.5f;

    static final double NODE_ITEM_HEIGHT = 0.55;
    static final double NODE_LABEL_HEIGHT = 0.15;
    static final double FILTER_HEIGHT = 0.8;

    private DisplayLayout() {}

    /**
     * Where a node's item display goes. Blocks are centred exactly on the model's front face, so 50% of the block sticks
     * out. Flat items are only 1px thick, so they sit just in front of the furthest-forward part of the model instead
     * (otherwise the chest latch pokes through them).
     */
    public static Placement nodeItem(Surface surface, BlockFace facing, Shape shape) {
        double out = shape == Shape.BLOCK ? surface.front : surface.protrusion + FACE_GAP;
        return place(horizontal(facing), out, NODE_ITEM_HEIGHT);
    }

    /** The label is flat text below the item (clear of the latch), just in front of the front face. */
    public static Placement nodeLabel(Surface surface, BlockFace facing) {
        return place(horizontal(facing), surface.front + FACE_GAP, NODE_LABEL_HEIGHT);
    }

    /** Filter display {@code index} (0-3) goes on the hopper's N, E, S, W sides in turn. */
    public static Placement filter(int index) {
        BlockFace face = HORIZONTAL[Math.floorMod(index, HORIZONTAL.length)];
        return place(face, Surface.HOPPER_SIDE.protrusion + FACE_GAP, FILTER_HEIGHT);
    }

    static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private static Placement place(BlockFace face, double out, double height) {
        return new Placement(
                0.5 + face.getModX() * out, height, 0.5 + face.getModZ() * out, yaw(face) + ITEM_YAW_OFFSET);
    }

    /**
     * Whether an item renders as a 3D block or a flat sheet. Solid blocks (cubes, stairs, slabs, glass, chests) render
     * in 3D; everything else, including non-solid blocks like torches and flowers, renders flat. Empty counts as flat.
     */
    public static Shape shapeOf(@Nullable ItemStack item) {
        return item != null && item.getType().isBlock() && item.getType().isSolid() ? Shape.BLOCK : Shape.FLAT;
    }

    /** Displays only sit on the four horizontal faces; up/down (e.g. barrels facing up) fall back to north. */
    public static BlockFace horizontal(BlockFace facing) {
        return switch (facing) {
            case NORTH, EAST, SOUTH, WEST -> facing;
            default -> BlockFace.NORTH;
        };
    }

    /** Minecraft yaw: 0 = south (+Z), 90 = west, 180 = north, -90 = east. */
    static float yaw(BlockFace face) {
        return switch (face) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }
}
