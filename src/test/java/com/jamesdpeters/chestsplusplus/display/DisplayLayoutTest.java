package com.jamesdpeters.chestsplusplus.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jamesdpeters.chestsplusplus.display.DisplayLayout.Placement;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout.Shape;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout.Surface;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class DisplayLayoutTest {

    @Test
    void nodeItemSitsJustOutsideTheFace() {
        Placement north = DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.NORTH, Shape.FLAT);
        assertThat(north.x()).isEqualTo(0.5);
        assertThat(north.z()).isCloseTo(-DisplayLayout.FACE_GAP, within(1e-9));
        assertThat(north.yaw()).isEqualTo(180f + DisplayLayout.ITEM_YAW_OFFSET);

        Placement east = DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.EAST, Shape.FLAT);
        assertThat(east.x()).isCloseTo(1 + DisplayLayout.FACE_GAP, within(1e-9));
        assertThat(east.z()).isEqualTo(0.5);
    }

    @Test
    void blocksAreCentredOnTheFrontFaceSoHalfSticksOut() {
        // Chest body front is 1px inside the block (z = 15/16 on the south face); full blocks at the boundary.
        assertThat(DisplayLayout.nodeItem(Surface.CHEST, BlockFace.SOUTH, Shape.BLOCK)
                        .z())
                .isCloseTo(15.0 / 16, within(1e-9));
        assertThat(DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.SOUTH, Shape.BLOCK)
                        .z())
                .isCloseTo(1.0, within(1e-9));
        assertThat(DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.NORTH, Shape.BLOCK)
                        .z())
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    void flatItemsSitInFrontOfTheChestLatch() {
        Placement south = DisplayLayout.nodeItem(Surface.CHEST, BlockFace.SOUTH, Shape.FLAT);
        assertThat(south.z()).isCloseTo(1 + DisplayLayout.FACE_GAP, within(1e-9));
        assertThat(south.yaw()).isEqualTo(DisplayLayout.ITEM_YAW_OFFSET);
    }

    @Test
    void verticalFacingsFallBackToNorth() {
        assertThat(DisplayLayout.horizontal(BlockFace.UP)).isEqualTo(BlockFace.NORTH);
        assertThat(DisplayLayout.nodeLabel(Surface.FULL_BLOCK, BlockFace.WEST).y())
                .isLessThan(DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.WEST, Shape.FLAT)
                        .y());
    }

    @Test
    void filterGridStartsTopLeftAsSeenByTheViewer() {
        // Looking at the south face you face north, so your left is west (smaller x).
        Placement first = DisplayLayout.filterCell(BlockFace.SOUTH, 0);
        Placement second = DisplayLayout.filterCell(BlockFace.SOUTH, 1);
        Placement nextRow = DisplayLayout.filterCell(BlockFace.SOUTH, DisplayLayout.FILTER_COLUMNS);
        assertThat(first.x()).isLessThan(second.x());
        assertThat(first.y()).isGreaterThan(nextRow.y());
        assertThat(first.z()).isGreaterThan(1.0);
        // Looking at the north face you face south, so your left is east (larger x).
        assertThat(DisplayLayout.filterCell(BlockFace.NORTH, 0).x())
                .isGreaterThan(DisplayLayout.filterCell(BlockFace.NORTH, 1).x());
        // Everything stays on the hopper bowl.
        for (int i = 0; i < DisplayLayout.FILTER_COLUMNS * DisplayLayout.FILTER_ROWS; i++) {
            assertThat(DisplayLayout.filterCell(BlockFace.EAST, i).y()).isBetween(10.0 / 16, 1.0);
        }
    }

    @Test
    void filterCellAtIsTheInverseOfFilterCell() {
        for (BlockFace face : DisplayLayout.HORIZONTAL) {
            for (int i = 0; i < DisplayLayout.FILTER_COLUMNS * DisplayLayout.FILTER_ROWS; i++) {
                Placement p = DisplayLayout.filterCell(face, i);
                // The hit point is on the block surface, just behind the display.
                double x = p.x() - face.getModX() * DisplayLayout.FACE_GAP;
                double z = p.z() - face.getModZ() * DisplayLayout.FACE_GAP;
                assertThat(DisplayLayout.filterCellAt(face, x, p.y(), z))
                        .as(face + " #" + i)
                        .isEqualTo(i);
            }
        }
        assertThat(DisplayLayout.filterCellAt(BlockFace.SOUTH, 0.5, 0.3, 1.0)).isEqualTo(-1); // below the bowl
        assertThat(DisplayLayout.filterCellAt(BlockFace.UP, 0.5, 1.0, 0.5)).isEqualTo(-1);
    }
}
