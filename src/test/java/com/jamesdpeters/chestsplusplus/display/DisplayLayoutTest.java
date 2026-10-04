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
        assertThat(north.z()).isCloseTo(-DisplayLayout.FLAT_ITEM_OFFSET, within(1e-9));
        assertThat(north.yaw()).isEqualTo(180f + DisplayLayout.ITEM_YAW_OFFSET);

        Placement east = DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.EAST, Shape.FLAT);
        assertThat(east.x()).isCloseTo(1 + DisplayLayout.FLAT_ITEM_OFFSET, within(1e-9));
        assertThat(east.z()).isEqualTo(0.5);
    }

    @Test
    void blocksAreCentredOnTheFrontFaceSoHalfIsInset() {
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
        // In front of the chest body (z = 15/16 on the south face) and further out than a block display. The exact
        // depth is tuned by FLAT_ITEM_OFFSET and the CHEST protrusion.
        assertThat(south.z())
                .isGreaterThan(15.0 / 16)
                .isGreaterThan(DisplayLayout.nodeItem(Surface.CHEST, BlockFace.SOUTH, Shape.BLOCK)
                        .z());
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
        Placement first = DisplayLayout.filterCell(BlockFace.SOUTH, 0, 0);
        Placement second = DisplayLayout.filterCell(BlockFace.SOUTH, 0, 1);
        Placement nextRow = DisplayLayout.filterCell(BlockFace.SOUTH, 1, 0);
        assertThat(first.x()).isLessThan(second.x());
        assertThat(first.y()).isGreaterThan(nextRow.y());
        // Flush against the face (a hair's gap for z-fighting) and turned to face outwards.
        assertThat(first.z()).isBetween(1.0, 1.01);
        assertThat(first.yaw()).isEqualTo(180f);
        // Column and row spacing are set independently.
        assertThat(second.x() - first.x()).isCloseTo(DisplayLayout.FILTER_COLUMN_PITCH, within(1e-9));
        assertThat(first.y() - nextRow.y()).isCloseTo(DisplayLayout.FILTER_ROW_PITCH, within(1e-9));
        // Looking at the north face you face south, so your left is east (larger x).
        assertThat(DisplayLayout.filterCell(BlockFace.NORTH, 0, 0).x())
                .isGreaterThan(DisplayLayout.filterCell(BlockFace.NORTH, 0, 1).x());
        // Everything stays on the hopper bowl, within the block's width.
        for (int row = 0; row < DisplayLayout.FILTER_ROWS; row++) {
            for (int column = 0; column < DisplayLayout.FILTER_COLUMNS; column++) {
                Placement cell = DisplayLayout.filterCell(BlockFace.EAST, row, column);
                assertThat(cell.y()).isBetween(10.0 / 16, 1.0);
                assertThat(cell.z()).isBetween(0.0, 1.0);
            }
        }
    }
}
