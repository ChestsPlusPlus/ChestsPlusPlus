package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rewrites a v2 {@code config.yml} into the v3 layout. It must run before settings load: otherwise {@code saveDefaultConfig} keeps the v2
 * file and v3 silently runs on defaults. The original is kept as {@code config-v2.yml}.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
public final class V2ConfigMigrator {

    private static final List<String> V3_SECTIONS = List.of("features", "linking", "chestlink", "autocraft", "filters", "limits", "worlds",
            "storage");
    private static final Set<String> V2_KEYS = Set.of("chestlinks-enabled", "autocrafters-enabled", "hopper-filters-enabled", "limit-chests",
            "limit-chestlinks-amount", "should-animate-all-chests", "display_chestlink_armour_stand", "display_autocraft_armour_stands",
            "set-filter-itemframe-invisible", "world-blacklist", "language-file", "update-checker", "update-checker-period");
    private static final List<String> DROPPED = List.of("update-checker-period", "set-filter-itemframe-invisible", "language-file");

    private V2ConfigMigrator() {}

    /** Converts the data folder's config.yml if it is a v2 one; a failure is logged and v3 carries on with what is there. */
    public static void migrate(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) return;
        YamlConfiguration v2 = YamlConfiguration.loadConfiguration(file);
        if (!isV2(v2)) return;
        try {
            File kept = unusedFile(plugin.getDataFolder(), "config-v2", ".yml");
            Files.move(file.toPath(), kept.toPath());
            plugin.saveResource("config.yml", false);
            YamlConfiguration v3 = YamlConfiguration.loadConfiguration(file);
            List<String> dropped = apply(v2, v3);
            v3.save(file);
            log.info("Converted the ChestsPlusPlus v2 config.yml to v3; the original is kept as {}", kept.getName());
            if (!dropped.isEmpty()) log.info("These v2 settings have no v3 equivalent and were dropped: {}", String.join(", ", dropped));
        } catch (IOException | IllegalArgumentException e) {
            log.error("Could not convert the ChestsPlusPlus v2 config.yml", e);
        }
    }

    static boolean isV2(ConfigurationSection config) {
        return V3_SECTIONS.stream().noneMatch(config::isConfigurationSection) && config.getKeys(false).stream().anyMatch(V2_KEYS::contains);
    }

    /** Copies v2's settings onto the v3 defaults, returning the v2 keys that were set but have nowhere to go. */
    static List<String> apply(ConfigurationSection v2, ConfigurationSection v3) {
        copyBoolean(v2, "chestlinks-enabled", v3, "features.chestlinks");
        copyBoolean(v2, "autocrafters-enabled", v3, "features.autocraft");
        copyBoolean(v2, "hopper-filters-enabled", v3, "features.hopper-filters");
        copyBoolean(v2, "should-animate-all-chests", v3, "chestlink.animate-all-nodes");
        copyBoolean(v2, "display_chestlink_armour_stand", v3, "chestlink.display.enabled");
        copyBoolean(v2, "display_autocraft_armour_stands", v3, "autocraft.display.enabled");
        if (v2.isBoolean("update-checker")) v3.set("update-checker.enabled", v2.getBoolean("update-checker"));
        if (v2.contains("limit-chests"))
            v3.set("limits.chestlink-default", v2.getBoolean("limit-chests") ? v2.getInt("limit-chestlinks-amount") : -1);
        if (v2.contains("world-blacklist"))
            v3.set("worlds.blacklist", v2.getStringList("world-blacklist").stream().filter(world -> !world.isBlank()).toList());
        List<String> dropped = new ArrayList<>();
        for (String key : DROPPED) if (v2.contains(key)) dropped.add(key);
        return dropped;
    }

    private static void copyBoolean(ConfigurationSection from, String fromKey, ConfigurationSection to, String toKey) {
        if (from.isBoolean(fromKey)) to.set(toKey, from.getBoolean(fromKey));
    }

    static File unusedFile(File folder, String base, String extension) {
        File file = new File(folder, base + extension);
        for (int n = 2; file.exists(); n++) file = new File(folder, base + "-" + n + extension);
        return file;
    }
}
