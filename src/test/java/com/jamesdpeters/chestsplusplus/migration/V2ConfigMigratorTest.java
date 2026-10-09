package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag(Tags.UNIT)
class V2ConfigMigratorTest {

    private static final String V2_CONFIG = """
            update-checker: false
            update-checker-period: 3600
            chestlinks-enabled: true
            autocrafters-enabled: false
            hopper-filters-enabled: true
            limit-chests: true
            limit-chestlinks-amount: 5
            should-animate-all-chests: false
            display_chestlink_armour_stand: true
            display_autocraft_armour_stands: false
            set-filter-itemframe-invisible: false
            world-blacklist:
            - ''
            - world_nether
            language-file: default
            """;

    @TempDir Path dir;

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void recognisesOnlyV2Configs() throws Exception {
        Settings.update(dir.resolve("config.yml"));

        assertThat(V2ConfigMigrator.isV2(yaml(V2_CONFIG))).isTrue();
        assertThat(V2ConfigMigrator.isV2(YamlConfiguration.loadConfiguration(dir.resolve("config.yml").toFile()))).isFalse();
        assertThat(V2ConfigMigrator.isV2(yaml("unrelated: 1"))).isFalse();
    }

    @Test
    void copiesV2SettingsIntoTheV3Layout() throws Exception {
        YamlConfiguration v3 = new YamlConfiguration();

        var dropped = V2ConfigMigrator.apply(yaml(V2_CONFIG), v3);

        Settings settings = Settings.parse(v3.saveToString());
        assertThat(settings.features().chestlinks()).isTrue();
        assertThat(settings.features().autocraft()).isFalse();
        assertThat(settings.limits().chestlinkDefault()).isEqualTo(5);
        assertThat(settings.chestlink().animateAllNodes()).isFalse();
        assertThat(settings.autocraft().display().enabled()).isFalse();
        assertThat(settings.isBlacklisted("world_nether")).isTrue();
        assertThat(settings.updateChecker().enabled()).isFalse();
        assertThat(v3.getStringList("worlds.blacklist")).containsExactly("world_nether");
        assertThat(dropped).containsExactly("update-checker-period", "set-filter-itemframe-invisible", "language-file");
    }

    @Test
    void anUnlimitedV2ServerStaysUnlimited() throws Exception {
        YamlConfiguration v3 = new YamlConfiguration();

        V2ConfigMigrator.apply(yaml("limit-chests: false\nlimit-chestlinks-amount: 0\n"), v3);

        assertThat(Settings.parse(v3.saveToString()).limits().chestlinkDefault()).isEqualTo(-1);
    }

    @Test
    void migratedFileLoadsWithV2ValuesAndV3Defaults() throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, V2_CONFIG);

        V2ConfigMigrator.migrate(dir.toFile());
        Settings settings = Settings.update(file);

        assertThat(dir.resolve("config-v2.yml")).hasContent(V2_CONFIG);
        assertThat(settings.features().autocraft()).isFalse();
        assertThat(settings.features().copperGolems()).isTrue();
        assertThat(settings.limits().chestlinkDefault()).isEqualTo(5);
        assertThat(settings.storage().flushIntervalSeconds()).isEqualTo(30);
        YamlConfiguration written = YamlConfiguration.loadConfiguration(file.toFile());
        assertThat(V2ConfigMigrator.isV2(written)).isFalse();
        assertThat(written.getBoolean("linking.consume-name-tags")).isTrue();
        assertThat(Files.readString(file)).contains("# Ticks between crafting attempts");
    }
}
