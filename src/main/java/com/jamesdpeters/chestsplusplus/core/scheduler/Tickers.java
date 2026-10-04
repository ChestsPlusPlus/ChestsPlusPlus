package com.jamesdpeters.chestsplusplus.core.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;

/** The plugin's central repeating tasks. Each body is guarded so one failure doesn't cancel the ticker. */
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

    /**
     * Runs {@code body} every {@code interval} ticks, passing it the interval. The interval is re-read each tick so a config reload
     * applies without restarting the ticker.
     */
    public void everyInterval(String name, IntSupplier interval, IntConsumer body) {
        int[] elapsed = {0};
        every(name, 1, () -> {
            int period = interval.getAsInt();
            if (++elapsed[0] < period) return;
            elapsed[0] = 0;
            body.accept(period);
        });
    }

    public void stopAll() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }
}
