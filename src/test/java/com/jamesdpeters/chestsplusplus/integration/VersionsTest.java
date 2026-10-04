package com.jamesdpeters.chestsplusplus.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class VersionsTest {

    @Test
    void comparesNumericallyAndPrefersReleases() {
        assertThat(Versions.isNewer("3.0.1", "3.0.0")).isTrue();
        assertThat(Versions.isNewer("v3.10.0", "3.9.9")).isTrue();
        assertThat(Versions.isNewer("3.0.0", "3.0.0-SNAPSHOT")).isTrue();
        assertThat(Versions.isNewer("3.0.0-SNAPSHOT", "3.0.0")).isFalse();
        assertThat(Versions.isNewer("2.9.9", "3.0.0")).isFalse();
        assertThat(Versions.compare("3.0", "3.0.0")).isZero();
    }

    @Test
    void parsesTheReleaseTag() {
        assertThat(UpdateChecker.parseTag("{\"url\":\"x\",\"tag_name\": \"v3.0.1\",\"name\":\"y\"}")).isEqualTo("v3.0.1");
        assertThat(UpdateChecker.parseTag("{}")).isNull();
    }
}
