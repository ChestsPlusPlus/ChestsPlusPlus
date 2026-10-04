package com.jamesdpeters.chestsplusplus.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/** Immutable view of {@code config.yml}. Swapped atomically on {@code /cpp reload}. */
public record Settings(
        Features features,
        ChestLink chestlink,
        AutoCraft autocraft,
        Filters filters,
        Limits limits,
        Set<String> worldBlacklist,
        Storage storage,
        boolean updateChecker,
        boolean metrics) {

    public record Features(boolean chestlinks, boolean autocraft, boolean hopperFilters) {}

    public record Display(boolean enabled, boolean label, float viewRange) {}

    public record ChestLink(boolean animateAllNodes, Display display) {}

    public record AutoCraft(Display display, int tickInterval) {}

    public record Filters(boolean displays) {}

    /** {@code -1} means unlimited. */
    public record Limits(int chestlinkDefault, int autocraftDefault) {}

    public record Storage(int flushIntervalSeconds, int maxSerialisationsPerTick) {}

    public static final Settings DEFAULTS = from(new MemoryConfiguration());

    public static Settings from(ConfigurationSection config) {
        return new Settings(
                new Features(
                        config.getBoolean("features.chestlinks", true),
                        config.getBoolean("features.autocraft", true),
                        config.getBoolean("features.hopper-filters", true)),
                new ChestLink(
                        config.getBoolean("chestlink.animate-all-nodes", true), display(config, "chestlink.display")),
                new AutoCraft(
                        display(config, "autocraft.display"),
                        clamp(config.getInt("autocraft.tick-interval", 20), 1, 1200)),
                new Filters(config.getBoolean("filters.displays", true)),
                new Limits(
                        Math.max(-1, config.getInt("limits.chestlink-default", -1)),
                        Math.max(-1, config.getInt("limits.autocraft-default", -1))),
                config.getStringList("worlds.blacklist").stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet()),
                new Storage(
                        clamp(config.getInt("storage.flush-interval-seconds", 30), 1, 3600),
                        clamp(config.getInt("storage.max-serialisations-per-tick", 16), 1, 1024)),
                config.getBoolean("update-checker.enabled", true),
                config.getBoolean("metrics.enabled", true));
    }

    public boolean isBlacklisted(String worldName) {
        return worldBlacklist.contains(worldName.toLowerCase(Locale.ROOT));
    }

    private static Display display(ConfigurationSection config, String path) {
        return new Display(config.getBoolean(path + ".enabled", true), config.getBoolean(path + ".label", true), (float)
                Math.clamp(config.getDouble(path + ".view-range", 0.5), 0.05, 16.0));
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }
}
