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
    void verticalFacingsFallBackToNorthAndFiltersCycleSides() {
        assertThat(DisplayLayout.horizontal(BlockFace.UP)).isEqualTo(BlockFace.NORTH);
        assertThat(DisplayLayout.filter(0).z()).isLessThan(0);
        assertThat(DisplayLayout.filter(1).x()).isGreaterThan(1);
        assertThat(DisplayLayout.filter(4)).isEqualTo(DisplayLayout.filter(0));
        assertThat(DisplayLayout.nodeLabel(Surface.FULL_BLOCK, BlockFace.WEST).y())
                .isLessThan(DisplayLayout.nodeItem(Surface.FULL_BLOCK, BlockFace.WEST, Shape.FLAT)
                        .y());
    }
}
