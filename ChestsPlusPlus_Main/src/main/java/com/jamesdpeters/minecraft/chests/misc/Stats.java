package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.PluginConfig;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

public class Stats {

    public static void addCharts(Metrics metrics) {
        metrics.addCustomChart(new SimplePie("chestlink-amount", () -> {
            int chestlinks = Config.getChestLink().getTotalLocations();
            return chestlinks + "";
        }));

        metrics.addCustomChart(new SimplePie("autocraft-amount", () -> {
            int locations = Config.getAutoCraft().getTotalLocations();
            return locations + "";
        }));

        metrics.addCustomChart(new SimplePie("update_checker_setting", () -> {
            if (PluginConfig.IS_UPDATE_CHECKER_ENABLED.get()) return "enabled";
            else return "disabled";
        }));

        metrics.addCustomChart(new SimplePie("language-file", PluginConfig.LANG_FILE::get));
    }
}
