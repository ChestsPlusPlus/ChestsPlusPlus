package com.jamesdpeters.chestsplusplus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class BlockMapTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final BlockMap<String> map = new BlockMap<>();

    @Test
    void putGetAndRemove() {
        BlockPos pos = new BlockPos(WORLD, -17, 70, 33);

        assertThat(map.put(pos, "a")).isNull();
        assertThat(map.get(pos)).isEqualTo("a");
        assertThat(map.get(WORLD, BlockPos.packed(-17, 70, 33))).isEqualTo("a");
        assertThat(map.put(pos, "b")).isEqualTo("a");
        assertThat(map.size()).isEqualTo(1);

        assertThat(map.remove(pos)).isEqualTo("b");
        assertThat(map.get(pos)).isNull();
        assertThat(map.remove(pos)).isNull();
        assertThat(map.inChunk(WORLD, pos.chunkKey())).isEmpty();
        assertThat(map.size()).isZero();
    }

    @Test
    void inChunkListsOneChunkInInsertionOrder() {
        BlockPos first = new BlockPos(WORLD, 5, 64, 5);
        BlockPos second = new BlockPos(WORLD, 0, 10, 15);
        BlockPos nextChunk = new BlockPos(WORLD, 16, 64, 5);
        map.put(first, "first");
        map.put(second, "second");
        map.put(nextChunk, "next");
        map.put(first, "replaced");

        assertThat(map.inChunk(WORLD, first.chunkKey())).containsExactly("replaced", "second");
        assertThat(map.inChunk(WORLD, nextChunk.chunkKey())).containsExactly("next");
        assertThat(map.inChunk(WORLD, BlockPos.chunkKey(9, 9))).isEmpty();
    }

    @Test
    void removeChunkReturnsItsPositionsAndLeavesTheRest() {
        BlockPos a = new BlockPos(WORLD, -1, 64, -1);
        BlockPos b = new BlockPos(WORLD, -16, 0, -16);
        BlockPos other = new BlockPos(WORLD, 0, 64, 0);
        map.put(a, "a");
        map.put(b, "b");
        map.put(other, "other");

        assertThat(map.removeChunk(WORLD, a.chunkKey())).containsExactly(a, b);
        assertThat(map.get(a)).isNull();
        assertThat(map.get(b)).isNull();
        assertThat(map.get(other)).isEqualTo("other");
        assertThat(map.removeChunk(WORLD, a.chunkKey())).isEmpty();
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    void worldsAreKeptApart() {
        BlockPos overworld = new BlockPos(WORLD, 3, 64, 3);
        BlockPos nether = new BlockPos(NETHER, 3, 64, 3);
        map.put(overworld, "overworld");
        map.put(nether, "nether");

        assertThat(map.get(WORLD, overworld.packed())).isEqualTo("overworld");
        assertThat(map.get(NETHER, nether.packed())).isEqualTo("nether");
        assertThat(map.removeChunk(WORLD, overworld.chunkKey())).containsExactly(overworld);
        assertThat(map.inChunk(NETHER, nether.chunkKey())).containsExactly("nether");
        assertThat(map.remove(overworld)).isNull();
        assertThat(map.get(nether)).isEqualTo("nether");
    }

    @Test
    void forEachValuesAndClear() {
        BlockPos a = new BlockPos(WORLD, 1, 2, 3);
        BlockPos b = new BlockPos(NETHER, -4, -5, -6);
        map.put(a, "a");
        map.put(b, "b");

        Map<BlockPos, String> seen = new HashMap<>();
        map.forEach(seen::put);
        assertThat(seen).containsExactlyInAnyOrderEntriesOf(Map.of(a, "a", b, "b"));
        assertThat(map.values()).containsExactlyInAnyOrder("a", "b");

        map.clear();
        assertThat(map.size()).isZero();
        assertThat(map.get(a)).isNull();
    }
}
