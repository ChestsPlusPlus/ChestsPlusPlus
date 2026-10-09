package com.jamesdpeters.chestsplusplus.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class SettingsTest {

    @TempDir Path dir;

    @Test
    void freshInstallWritesEveryKeyWithItsComment() throws IOException {
        Path file = dir.resolve("config.yml");

        Settings settings = Settings.update(file);

        assertThat(settings).usingRecursiveComparison().isEqualTo(Settings.defaults());
        String text = Files.readString(file);
        assertThat(text).startsWith("# ChestsPlusPlus v3 configuration. Reload with /cpp reload.")
                .contains("  # Copper golems take items from linked copper chests", "  # Ticks between crafting attempts", "# -1 = unlimited.");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertThat(yaml.getKeys(true)).contains("features.hopper-filters", "features.copper-golems", "linking.consume-name-tags",
                "linking.consume-signs", "chestlink.animate-all-nodes", "chestlink.display.view-range", "autocraft.display.label",
                "autocraft.tick-interval", "filters.displays", "limits.chestlink-default", "limits.autocraft-default", "worlds.blacklist",
                "storage.flush-interval-seconds", "update-checker.enabled", "metrics.enabled");
        assertThat(yaml.getDouble("chestlink.display.view-range")).isEqualTo(0.5);
        assertThat(yaml.getInt("limits.chestlink-default")).isEqualTo(-1);
    }

    @Test
    void missingKeysAreAddedAndUserValuesKept() throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                features:
                  chestlinks: false
                worlds:
                  blacklist: [World_Nether]
                """);

        Settings settings = Settings.update(file);

        assertThat(settings.features().chestlinks()).isFalse();
        assertThat(settings.features().copperGolems()).isTrue();
        assertThat(settings.linking().consumeNameTags()).isTrue();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        assertThat(yaml.getBoolean("features.chestlinks", true)).isFalse();
        assertThat(yaml.getBoolean("features.copper-golems")).isTrue();
        assertThat(yaml.getStringList("worlds.blacklist")).containsExactly("World_Nether");
        assertThat(yaml.getInt("storage.flush-interval-seconds")).isEqualTo(30);
    }

    @Test
    void parsesAndClamps() {
        Settings settings = Settings.parse("""
                features: { autocraft: false }
                linking: { consume-name-tags: false, consume-signs: true }
                chestlink: { display: { view-range: 100 } }
                autocraft: { display: { view-range: 0.01 }, tick-interval: 0 }
                limits: { chestlink-default: 5, autocraft-default: -7 }
                worlds: { blacklist: [World_Nether, creative] }
                storage: { flush-interval-seconds: 10000 }
                update-checker: { enabled: false }
                """);

        assertThat(settings.features().autocraft()).isFalse();
        assertThat(settings.features().chestlinks()).isTrue();
        assertThat(settings.features().copperGolems()).isTrue();
        assertThat(settings.linking().consumeNameTags()).isFalse();
        assertThat(settings.linking().consumeSigns()).isTrue();
        assertThat(settings.chestlink().display().viewRange()).isEqualTo(16.0f);
        assertThat(settings.autocraft().display().viewRange()).isEqualTo(0.05f);
        assertThat(settings.autocraft().tickInterval()).isEqualTo(1);
        assertThat(settings.limits().chestlinkDefault()).isEqualTo(5);
        assertThat(settings.limits().autocraftDefault()).isEqualTo(-1);
        assertThat(settings.isBlacklisted("world_nether")).isTrue();
        assertThat(settings.isBlacklisted("CREATIVE")).isTrue();
        assertThat(settings.isBlacklisted("world")).isFalse();
        assertThat(settings.storage().flushIntervalSeconds()).isEqualTo(3600);
        assertThat(settings.updateChecker().enabled()).isFalse();
        assertThat(settings.metrics().enabled()).isTrue();
    }

    @Test
    void aFileWithOnlyCommentsGetsTheDefaults() throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "# nothing set\n\n");

        assertThat(Settings.update(file)).usingRecursiveComparison().isEqualTo(Settings.defaults());
        assertThat(Files.readString(file)).contains("hopper-filters: true");
    }
}
