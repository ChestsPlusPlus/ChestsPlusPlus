package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.migration.V2PendingLocations.LocationRow;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class V2PendingLocationsTest {

    private final V2PendingLocations pending = new V2PendingLocations();

    @Test
    void inChunkOnlyReturnsThatWorldsChunk() {
        LocationRow here = new LocationRow(1, "world", -1, 64, 15);
        LocationRow sameChunkOtherGroup = new LocationRow(2, "world", -16, 70, 0);
        LocationRow nextChunk = new LocationRow(1, "world", 0, 64, 15);
        LocationRow otherWorld = new LocationRow(1, "nether", -1, 64, 15);
        List.of(here, sameChunkOtherGroup, nextChunk, otherWorld).forEach(pending::add);

        assertThat(pending.inChunk("world", BlockPos.chunkKey(-1, 0))).containsExactlyInAnyOrder(here, sameChunkOtherGroup);
        assertThat(pending.inChunk("world", BlockPos.chunkKey(0, 0))).containsExactly(nextChunk);
        assertThat(pending.inChunk("nether", BlockPos.chunkKey(-1, 0))).containsExactly(otherWorld);
        assertThat(pending.inChunk("end", BlockPos.chunkKey(-1, 0))).isEmpty();
    }

    @Test
    void removalsAndReloadsKeepTheChunkIndexInStep() {
        long chunk = BlockPos.chunkKey(0, 0);
        LocationRow a = new LocationRow(1, "world", 1, 64, 1);
        LocationRow b = new LocationRow(2, "world", 2, 64, 2);
        pending.add(a);
        pending.add(b);

        pending.remove(a);
        assertThat(pending.inChunk("world", chunk)).containsExactly(b);
        pending.removeGroup(2);
        assertThat(pending.inChunk("world", chunk)).isEmpty();

        pending.add(a);
        pending.load(List.of(b));
        assertThat(pending.inChunk("world", chunk)).containsExactly(b);
    }
}
