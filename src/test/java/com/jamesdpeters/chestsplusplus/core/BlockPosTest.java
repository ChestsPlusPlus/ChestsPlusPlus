package com.jamesdpeters.chestsplusplus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag(Tags.UNIT)
class BlockPosTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @ParameterizedTest
    @CsvSource({"0,0,0", "1,-64,-1", "-1,319,1", "30000000,2047,-30000000", "-33554432,-2048,33554431", "123456,-1,-654321"})
    void packRoundTrips(int x, int y, int z) {
        BlockPos pos = new BlockPos(WORLD, x, y, z);

        assertThat(BlockPos.unpack(WORLD, pos.packed())).isEqualTo(pos);
    }

    @Test
    void distinctPositionsPackDistinctly() {
        assertThat(new BlockPos(WORLD, 1, 0, 0).packed()).isNotEqualTo(new BlockPos(WORLD, 0, 1, 0).packed())
                .isNotEqualTo(new BlockPos(WORLD, 0, 0, 1).packed());
        assertThat(new BlockPos(WORLD, -1, 0, 0).packed()).isNotEqualTo(new BlockPos(WORLD, 0, 0, -1).packed());
    }

    @Test
    void chunkKeyMatchesPaperLayout() {
        BlockPos pos = new BlockPos(WORLD, -17, 70, 33);

        assertThat(pos.chunkX()).isEqualTo(-2);
        assertThat(pos.chunkZ()).isEqualTo(2);
        assertThat(pos.chunkKey()).isEqualTo((-2 & 0xFFFFFFFFL) | (2L << 32));
    }

    @Test
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new BlockPos(WORLD, 0, 4096, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockPos(WORLD, 1 << 25, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
