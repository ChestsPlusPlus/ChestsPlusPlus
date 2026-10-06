package com.jamesdpeters.chestsplusplus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class ShutdownTest {

    private final Shutdown shutdown = new Shutdown();
    private final List<String> ran = new ArrayList<>();

    @Test
    void runsStepsInReverseOrder() {
        shutdown.add("database", () -> ran.add("database"));
        shutdown.add("tickers", () -> ran.add("tickers"));

        shutdown.run();

        assertThat(ran).containsExactly("tickers", "database");
    }

    @Test
    void aFailingStepDoesNotSkipTheRest() {
        shutdown.add("database", () -> ran.add("database"));
        shutdown.add("displays", () -> {
            throw new IllegalStateException("boom");
        });

        shutdown.run();

        assertThat(ran).containsExactly("database");
    }

    @Test
    void runsEachStepOnce() {
        shutdown.add("database", () -> ran.add("database"));

        shutdown.run();
        shutdown.run();

        assertThat(ran).containsExactly("database");
    }
}
