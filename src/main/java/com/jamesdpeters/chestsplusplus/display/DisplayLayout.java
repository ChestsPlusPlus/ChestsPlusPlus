package com.jamesdpeters.chestsplusplus.display;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Pure layout maths for node and filter displays: where, relative to the block's minimum corner, a display sits on a
 * face, and which yaw makes it face outward.
 */
public final class DisplayLayout {

    /** Placement of one display: offset from the block's minimum corner, and entity yaw in degrees. */
    public record Placement(double x, double y, double z, float yaw) {}

    /** Which model the display sits on. Distances are from the block centre, in blocks. */
    public enum Surface {
        /**
         * Chests: the body's front is 1px inside the block; the latch sticks out 1px to the block boundary. Blocks are
         * centred on the body's front (half inset into the chest); flat items sit in front of the latch.
         */
        CHEST(7.0 / 16, 7.5 / 16),
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
        /** Rendered as a 3D model (cubes, stairs, hoppers...). */
        BLOCK,
        /** Rendered as a flat 1px sheet (most items): placed in front of anything protruding, like the chest latch. */
        FLAT
    }

    /** Gap between the face and the display to avoid z-fighting. */
    static final double FACE_GAP = 0.02;

    /**
     * How far flat item displays sit in front of the furthest-forward part of the model (the chest latch, or the face
     * of a full block), in blocks. Make it smaller to tuck items closer; negative values push them into the latch
     * (1px = 0.0625).
     */
    public static final double FLAT_ITEM_OFFSET = 0.02;

    /**
     * Added to the facing yaw. Flip to 180 if displays turn out to face into the block.
     */
    static final float ITEM_YAW_OFFSET = 0f;

    /**
     * Uniform scale of node item displays. Uniform (not flattened) so they render in 3D like an item frame: blocks as
     * small cubes and items with their pixel thickness.
     */
    public static final float NODE_ITEM_SCALE = 0.5f;

    static final double NODE_ITEM_HEIGHT = 0.55;
    static final double NODE_LABEL_HEIGHT = 0.15;
    public static final float NODE_LABEL_SCALE = 0.35f;
    /**
     * Label wrap width in font pixels: a chest's 14px front at {@link #NODE_LABEL_SCALE} (a font pixel is 1/40 of a block at
     * scale 1), so long names wrap onto more lines instead of running past the block's sides.
     */
    public static final int NODE_LABEL_LINE_WIDTH = Math.round(14 / 16f * 40 / NODE_LABEL_SCALE);

    /**
     * Hopper filter grid on each side of the bowl: 2 rows (Allow, Deny) of 10 columns: a green/red pane marking the
     * row, then up to 9 entries (the editor's maximum per row).
     */
    public static final int FILTER_COLUMNS = 10;

    public static final int FILTER_ROWS = 2;
    /**
     * Scale of a filter icon. Filter displays use the GUI transform (they look like inventory icons), where an item
     * spans about one block at scale 1.
     */
    public static final float FILTER_ITEM_SCALE = 0.07f;
    /** Distance between neighbouring icons in a row. */
    static final double FILTER_COLUMN_PITCH = 0.085;
    /** Distance between the two rows. */
    static final double FILTER_ROW_PITCH = 0.1;
    /** Centre of the first (leftmost) column, relative to the face's centre. */
    static final double FILTER_FIRST_COLUMN = -0.45;
    /** Centre height of the top row of icons; both rows stay on the hopper bowl (y 10px..16px). */
    static final double FILTER_TOP_ROW_Y = 0.92;
    /** Icons are flattened onto the face; this tiny gap only avoids z-fighting with the hopper texture. */
    static final double FILTER_FACE_GAP = 0.003;
    /**
     * GUI-transform icons render facing the opposite way to FIXED ones, so without this they face into the hopper and
     * are seen from behind (block icons look upside down, items mirrored).
     */
    static final float FILTER_YAW_OFFSET = 180f;

    private DisplayLayout() {}

    /**
     * Where a node's item display goes. Blocks are centred on the model's front face, so half is inset into the
     * container and half sticks out. Flat items are only 1px thick, so they sit just in front of the furthest-forward
     * part of the model instead (otherwise the chest latch pokes through them).
     */
    public static Placement nodeItem(Surface surface, BlockFace facing, Shape shape) {
        double out = shape == Shape.BLOCK ? surface.front : surface.protrusion + FLAT_ITEM_OFFSET;
        return place(horizontal(facing), out, NODE_ITEM_HEIGHT);
    }

    /** The label is flat text below the item (clear of the latch), just in front of the front face. */
    public static Placement nodeLabel(Surface surface, BlockFace facing) {
        return place(horizontal(facing), surface.front + FACE_GAP, NODE_LABEL_HEIGHT);
    }

    /**
     * Where the filter icon at {@code row}, {@code column} goes on {@code face} of a hopper: a grid on the bowl, read
     * like text by someone looking at that face (column 0 is their left, row 0 the top).
     */
    public static Placement filterCell(BlockFace face, int row, int column) {
        double alongRight = FILTER_FIRST_COLUMN + column * FILTER_COLUMN_PITCH; // from the viewer's left
        BlockFace right = rightOf(face);
        double out = Surface.HOPPER_SIDE.protrusion + FILTER_FACE_GAP;
        return new Placement(0.5 + face.getModX() * out + right.getModX() * alongRight, FILTER_TOP_ROW_Y - row * FILTER_ROW_PITCH,
                0.5 + face.getModZ() * out + right.getModZ() * alongRight, yaw(face) + FILTER_YAW_OFFSET);
    }

    /** The direction to the right of someone looking at {@code face} from outside (they face the opposite way). */
    static BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST; // looking south at the north face, right is west
            case EAST -> BlockFace.NORTH;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    public static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private static Placement place(BlockFace face, double out, double height) {
        return new Placement(0.5 + face.getModX() * out, height, 0.5 + face.getModZ() * out, yaw(face) + ITEM_YAW_OFFSET);
    }

    /**
     * Whether an item renders as a 3D block model or a flat sprite. Full cubes (logs, stone, planks...) and the usual
     * 3D-model shapes (glass, leaves, slabs, stairs, walls, fences, chests, shulker boxes) are blocks. Everything else
     * is flat, including solid blocks whose item is a 2D icon (hoppers, cauldrons, doors, brewing stands). Empty counts
     * as flat. A heuristic: Paper doesn't expose item model types.
     */
    public static Shape shapeOf(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return Shape.FLAT;
        Material type = item.getType();
        if (!type.isBlock()) return Shape.FLAT;
        if (type.isOccluding()) return Shape.BLOCK;
        for (Tag<Material> tag : BlockModels.TAGS) if (tag.isTagged(type)) return Shape.BLOCK;
        return BlockModels.MATERIALS.contains(type) ? Shape.BLOCK : Shape.FLAT;
    }

    /**
     * The tag and material sets behind {@link #shapeOf}. Kept in a holder so they load on first use: {@link Tag} constants need a running
     * server, and the rest of this class must stay loadable in plain unit tests.
     */
    private static final class BlockModels {
        /** Non-occluding blocks whose items still render as 3D models. */
        static final List<Tag<Material>> TAGS = List.of(Tag.SLABS, Tag.STAIRS, Tag.WALLS, Tag.FENCES, Tag.FENCE_GATES, Tag.LEAVES, Tag.IMPERMEABLE,
                Tag.SHULKER_BOXES);

        static final Set<Material> MATERIALS = Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.GLASS,
                Material.TINTED_GLASS, Material.SLIME_BLOCK, Material.HONEY_BLOCK, Material.ICE, Material.SNOW_BLOCK);

        private BlockModels() {}
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
