package com.jamesdpeters.chestsplusplus.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static YamlConfiguration bundled() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(V2ConfigMigratorTest.class.getResourceAsStream("/config.yml")),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }

    @Test
    void recognisesOnlyV2Configs() throws Exception {
        assertThat(V2ConfigMigrator.isV2(yaml(V2_CONFIG))).isTrue();
        assertThat(V2ConfigMigrator.isV2(bundled())).isFalse();
        assertThat(V2ConfigMigrator.isV2(yaml("unrelated: 1"))).isFalse();
    }

    @Test
    void copiesV2SettingsOntoTheV3Defaults() throws Exception {
        YamlConfiguration v3 = bundled();

        var dropped = V2ConfigMigrator.apply(yaml(V2_CONFIG), v3);

        Settings settings = Settings.from(v3);
        assertThat(settings.features().chestlinks()).isTrue();
        assertThat(settings.features().autocraft()).isFalse();
        assertThat(settings.limits().chestlinkDefault()).isEqualTo(5);
        assertThat(settings.chestlink().animateAllNodes()).isFalse();
        assertThat(settings.autocraft().display().enabled()).isFalse();
        assertThat(settings.isBlacklisted("world_nether")).isTrue();
        assertThat(v3.getStringList("worlds.blacklist")).containsExactly("world_nether");
        assertThat(v3.getBoolean("update-checker.enabled")).isFalse();
        assertThat(dropped).containsExactly("update-checker-period", "set-filter-itemframe-invisible", "language-file");
    }

    @Test
    void anUnlimitedV2ServerStaysUnlimited() throws Exception {
        YamlConfiguration v3 = bundled();

        V2ConfigMigrator.apply(yaml("limit-chests: false\nlimit-chestlinks-amount: 0\n"), v3);

        assertThat(Settings.from(v3).limits().chestlinkDefault()).isEqualTo(-1);
    }
}
