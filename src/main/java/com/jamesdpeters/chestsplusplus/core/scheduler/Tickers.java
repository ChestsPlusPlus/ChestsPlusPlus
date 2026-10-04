package com.jamesdpeters.chestsplusplus.core.scheduler;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** The plugin's central repeating tasks. Each body is guarded so one failure doesn't cancel the ticker. */
@Slf4j(topic = ChestsPlusPlus.NAME)
public final class Tickers {

    private final Plugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public Tickers(Plugin plugin) {
        this.plugin = plugin;
    }

    public void every(String name, long periodTicks, Runnable body) {
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                body.run();
            } catch (RuntimeException e) {
                log.error("Ticker '{}' failed", name, e);
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
