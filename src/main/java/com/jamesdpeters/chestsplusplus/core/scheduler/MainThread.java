package com.jamesdpeters.chestsplusplus.core.scheduler;

import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;

/** Runs work on the server's main thread. Work is dropped once the plugin is disabled, since Bukkit refuses tasks from then on. */
@RequiredArgsConstructor
public final class MainThread {

    private final Plugin plugin;

    /** Runs {@code task} now when already on the main thread, otherwise on the next tick. */
    public void run(Runnable task) {
        if (!plugin.getServer().isPrimaryThread()) nextTick(task);
        else if (plugin.isEnabled()) task.run();
    }

    /** Runs {@code task} on the next tick even from the main thread, e.g. once the current event has finished. */
    public void nextTick(Runnable task) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
