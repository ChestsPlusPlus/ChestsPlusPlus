package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bstats.bukkit.Metrics;

public class Stats {

    public static void addCharts(Metrics metrics){
        metrics.addCustomChart(new Metrics.SimplePie("chestlink-amount", () -> {
            int chestlinks = Config.getTotalChestLinks();
            return chestlinks+"";
        }));

        metrics.addCustomChart(new Metrics.SimplePie("update_checker_setting", () -> {
            if(Settings.isUpdateCheckEnabled()) return "enabled";
            else return "disabled";
        }));
    }
}
