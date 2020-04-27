package com.jamesdpeters.minecraft.chests.misc;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import org.bstats.bukkit.Metrics;

public class Stats {

    public static void addCharts(Metrics metrics){
        metrics.addCustomChart(new Metrics.SimplePie("chestlink-amount", () -> {
            int chestlinks = Config.getTotalChestLinks();
            return chestlinks+"";
        }));
    }
}
