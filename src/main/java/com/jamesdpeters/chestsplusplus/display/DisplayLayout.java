package com.jamesdpeters.chestsplusplus.display;

import org.bukkit.block.BlockFace;

/**
 * Pure layout maths for node and filter displays: where, relative to the block's minimum corner, a display sits on a
 * face, and which yaw makes it face outward. Spike S4 picks the constants; see the plan (§13, S4).
 */
public final class DisplayLayout {

    /** Placement of one display: offset from the block's minimum corner, and entity yaw in degrees. */
    public record Placement(double x, double y, double z, float yaw) {}

    /** Which model the display sits on. */
    public enum Surface {
        /**
         * Chests: the body's front is 1px inside the block, but the latch sticks out 1px to the block boundary, so the
         * display has to sit in front of the latch (a little past the boundary) or the latch pokes through it.
         */
        CHEST(-1.0 / 64),
        /** Barrels, crafting tables: full cubes. */
        FULL_BLOCK(0),
        /** Hopper bowl sides (filters), upper part of the block. */
        HOPPER_SIDE(0);

        private final double inset;

        Surface(double inset) {
            this.inset = inset;
        }
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
     * small cubes and items with their pixel thickness. Centred on the face, half of a block's depth sits inside the
     * container.
     */
    public static final float NODE_ITEM_SCALE = 0.5f;

    static final double NODE_ITEM_HEIGHT = 0.55;
    static final double NODE_LABEL_HEIGHT = 0.15;
    static final double FILTER_HEIGHT = 0.8;

    private DisplayLayout() {}

    public static Placement nodeItem(Surface surface, BlockFace facing) {
        return onFace(surface, horizontal(facing), NODE_ITEM_HEIGHT, 0);
    }

    public static Placement nodeLabel(Surface surface, BlockFace facing) {
        return onFace(surface, horizontal(facing), NODE_LABEL_HEIGHT, FACE_GAP / 2);
    }

    /** Filter display {@code index} (0-3) goes on the hopper's N, E, S, W sides in turn. */
    public static Placement filter(int index) {
        BlockFace face = HORIZONTAL[Math.floorMod(index, HORIZONTAL.length)];
        return onFace(Surface.HOPPER_SIDE, face, FILTER_HEIGHT, 0);
    }

    static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private static Placement onFace(Surface surface, BlockFace face, double height, double extraGap) {
        double out = 0.5 - surface.inset + FACE_GAP + extraGap;
        return new Placement(
                0.5 + face.getModX() * out, height, 0.5 + face.getModZ() * out, yaw(face) + ITEM_YAW_OFFSET);
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
