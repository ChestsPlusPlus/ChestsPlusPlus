package com.jamesdpeters.chestsplusplus.autocraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class CraftSchedulerTest {

    private static final BlockPos NODE = new BlockPos(UUID.randomUUID(), 0, 64, 0);

    private final CraftScheduler scheduler = new CraftScheduler();

    @Test
    void backoffDoublesUpToTheCap() {
        assertThat(List.of(1, 2, 3, 4, 5, 6, 7, 8)).map(failures -> CraftScheduler.backoff(20, failures))
                .containsExactly(20L, 40L, 80L, 100L, 100L, 100L, 100L, 100L);
    }

    @Test
    void backoffNeverUndercutsALongInterval() {
        assertThat(CraftScheduler.backoff(1200, 1)).isEqualTo(1200);
        assertThat(CraftScheduler.backoff(1200, 5)).isEqualTo(1200);
    }

    @Test
    void aFailedNodeWithALongIntervalWaitsTheWholeInterval() {
        scheduler.failed(NODE, 1200, List.of(), List.of());

        for (int tick = 1; tick < 1200; tick++) assertThat(scheduler.advance()).isEmpty();
        assertThat(scheduler.advance()).containsExactly(NODE);
    }
}
