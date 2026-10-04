package com.jamesdpeters.chestsplusplus.integration;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** bStats (plugin id 7166, kept from v2). Never allowed to break enable. */
public final class MetricsService {

    public static final int PLUGIN_ID = 7166;

    private @Nullable Metrics metrics;

    public void start(JavaPlugin plugin, Services services) {
        if (!services.settings().metrics()) return;
        try {
            Metrics created = new Metrics(plugin, PLUGIN_ID);
            created.addCustomChart(new SingleLineChart(
                    "chestlinks",
                    () -> services.groups().all(GroupType.CHESTLINK).size()));
            created.addCustomChart(new SingleLineChart(
                    "autocrafters",
                    () -> services.groups().all(GroupType.AUTOCRAFT).size()));
            created.addCustomChart(
                    new SingleLineChart("linked_blocks", () -> services.nodes().size()));
            created.addCustomChart(new SimplePie(
                    "hopper_filters_enabled",
                    () -> String.valueOf(services.settings().features().hopperFilters())));
            metrics = created;
        } catch (RuntimeException | LinkageError e) {
            plugin.getSLF4JLogger().debug("bStats could not start", e);
        }
    }

    public void stop() {
        if (metrics != null) metrics.shutdown();
        metrics = null;
    }
}
