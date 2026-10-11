package com.jamesdpeters.chestsplusplus.autocraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class CraftSchedulerTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final BlockPos NODE = new BlockPos(WORLD, 0, 64, 0);
    private static final long GROUP = 1L;

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

    @Test
    void groupChangeRetriesEveryWatcherNextTickInOrder() {
        List<BlockPos> nodes = IntStream.range(0, 50).mapToObj(i -> new BlockPos(WORLD, i, 64, 0)).toList();
        nodes.forEach(node -> scheduler.failed(node, 20, List.of(), List.of(GROUP)));

        scheduler.groupChanged(GROUP);

        assertThat(scheduler.anyWaiting()).isFalse();
        assertThat(scheduler.advance()).containsExactlyElementsOf(nodes);
        for (int tick = 0; tick < CraftScheduler.MAX_BACKOFF_TICKS; tick++) assertThat(scheduler.advance()).isEmpty();
    }

    @Test
    void nodeWatchingAGroupTwiceIsRetriedOnce() {
        scheduler.failed(NODE, 20, List.of(), List.of(GROUP, GROUP));

        scheduler.groupChanged(GROUP);

        assertThat(scheduler.anyWaiting()).isFalse();
        assertThat(scheduler.advance()).containsExactly(NODE);
    }

    @Test
    void forgottenNodeIsNeitherDueNorWatching() {
        scheduler.failed(NODE, 1, List.of(), List.of(GROUP));

        scheduler.forget(NODE);

        assertThat(scheduler.anyWaiting()).isFalse();
        assertThat(scheduler.advance()).isEmpty();
    }
}
