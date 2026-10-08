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
    void comparesPreReleaseIdentifiersBySemVerPrecedence() {
        assertThat(Versions.compare("3.0.0-beta.10", "3.0.0-beta.9")).isPositive();
        assertThat(Versions.compare("3.0.0-beta.9", "3.0.0-beta.10")).isNegative();
        assertThat(Versions.compare("3.0.0", "3.0.0-beta.2")).isPositive();
        assertThat(Versions.compare("3.0.0-beta.1", "3.0.0-alpha.1")).isPositive();
        assertThat(Versions.compare("3.0.0-rc.1", "3.0.0-beta.5")).isPositive();
        assertThat(Versions.compare("3.0.0-rc.10", "3.0.0-rc.9")).isPositive();
        assertThat(Versions.compare("3.0.0-beta.1", "3.0.0-beta")).isPositive();
        assertThat(Versions.compare("3.0.0-beta.1", "3.0.0-beta.alpha")).isNegative();
        assertThat(Versions.compare("3.0.1-beta.1", "3.0.0")).isPositive();
        assertThat(Versions.compare("3.0.0-beta.1", "3.0.0-BETA.1")).isZero();
        assertThat(Versions.compare("3.0.0-beta.99999999999999999999", "3.0.0-beta.100000000000000000000")).isNegative();
        assertThat(Versions.compare("3.0.0-beta.9", "3.0.0-beta.1a")).isNegative();
    }

    @Test
    void ignoresBuildMetadata() {
        assertThat(Versions.compare("3.0.0+build.1", "3.0.0+build.2")).isZero();
        assertThat(Versions.compare("v3.0.0-beta.1+build.1", "3.0.0-BETA.1+build.2")).isZero();
    }

    @Test
    void parsesTheReleaseTag() {
        assertThat(UpdateChecker.parseTag("{\"url\":\"x\",\"tag_name\": \"v3.0.1\",\"name\":\"y\"}")).isEqualTo("v3.0.1");
        assertThat(UpdateChecker.parseTag("{}")).isNull();
    }
}
