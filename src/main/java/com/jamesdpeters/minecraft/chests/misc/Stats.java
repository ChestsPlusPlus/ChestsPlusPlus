package com.jamesdpeters.minecraft.chests.misc;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;

import java.util.concurrent.Callable;

public class Stats {

    public static void addCharts(Metrics metrics){
        metrics.addCustomChart(new Metrics.SimplePie("chestlink-amount", () -> {
            int chestlinks = Config.getTotalChestLinks();
            return chestlinks+"";
        }));
    }
}
