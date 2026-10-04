package com.jamesdpeters.chestsplusplus.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class SettingsTest {

    @Test
    void bundledConfigMatchesDefaults() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }

        assertThat(Settings.from(yaml)).isEqualTo(Settings.DEFAULTS);
    }

    @Test
    void parsesAndClamps() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                features: { autocraft: false }
                linking: { consume-name-tags: false }
                chestlink: { display: { view-range: 100 } }
                autocraft: { tick-interval: 0 }
                limits: { chestlink-default: 5, autocraft-default: -7 }
                worlds: { blacklist: [World_Nether, creative] }
                storage: { flush-interval-seconds: 10 }
                """);

        Settings settings = Settings.from(yaml);

        assertThat(settings.features().autocraft()).isFalse();
        assertThat(settings.features().chestlinks()).isTrue();
        assertThat(settings.linking().consumeNameTags()).isFalse();
        assertThat(settings.chestlink().display().viewRange()).isEqualTo(16.0f);
        assertThat(settings.autocraft().tickInterval()).isEqualTo(1);
        assertThat(settings.limits().chestlinkDefault()).isEqualTo(5);
        assertThat(settings.limits().autocraftDefault()).isEqualTo(-1);
        assertThat(settings.isBlacklisted("world_nether")).isTrue();
        assertThat(settings.isBlacklisted("CREATIVE")).isTrue();
        assertThat(settings.isBlacklisted("world")).isFalse();
        assertThat(settings.storage().flushIntervalSeconds()).isEqualTo(10);
    }
}
