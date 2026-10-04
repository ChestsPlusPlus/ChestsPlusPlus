package com.jamesdpeters.chestsplusplus.core.scheduler;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;

/**
 * The plugin's few central repeating tasks (plan §9: never one task per group or node). Each tick body is guarded so
 * one failure doesn't cancel the ticker.
 */
public final class Tickers {

    private final Plugin plugin;
    private final Logger logger;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public Tickers(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void every(String name, long periodTicks, Runnable body) {
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                body.run();
            } catch (RuntimeException e) {
                logger.error("Ticker '{}' failed", name, e);
            }
        }, periodTicks, periodTicks));
    }

    public int count() {
        return tasks.size();
    }

    public void stopAll() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }
}
