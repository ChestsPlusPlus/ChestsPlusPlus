package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.PluginConfig;
import org.bstats.bukkit.Metrics;

public class Stats {

    public static void addCharts(Metrics metrics){
        metrics.addCustomChart(new Metrics.SimplePie("chestlink-amount", () -> {
            int chestlinks = Config.getChestLink().getTotalLocations();
            return chestlinks+"";
        }));

        metrics.addCustomChart(new Metrics.SimplePie("autocraft-amount", () -> {
            int locations = Config.getAutoCraft().getTotalLocations();
            return locations+"";
        }));

        metrics.addCustomChart(new Metrics.SimplePie("update_checker_setting", () -> {
            if(PluginConfig.IS_UPDATE_CHECKER_ENABLED.get()) return "enabled";
            else return "disabled";
        }));

        metrics.addCustomChart(new Metrics.SimplePie("language-file", PluginConfig.LANG_FILE::get));
    }
}
