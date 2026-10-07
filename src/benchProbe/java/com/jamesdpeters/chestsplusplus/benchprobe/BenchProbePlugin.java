package com.jamesdpeters.chestsplusplus.benchprobe;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Measures the benchmark: {@code /benchprobe start} opens a window, {@code /benchprobe stop} closes it and writes {@code result.json}. It
 * listens to nothing but the tick end, so a server without ChestsPlusPlus does no extra work for it (Paper only fires hopper move events
 * when something listens).
 */
public final class BenchProbePlugin extends JavaPlugin implements Listener {

    public static final String RESULT_FILE = "result.json";

    private @Nullable Window window;
    /** The thread plugins are enabled on, which is the one that ticks. */
    private long serverThread;

    @Override
    public void onEnable() {
        serverThread = Thread.currentThread().threadId();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    void onTickEnd(ServerTickEndEvent event) {
        if (window != null) window.tick(event.getTickDuration());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        switch (args[0]) {
            case "start" -> {
                window = new Window(serverThread, barrelContents());
                sender.sendMessage("benchprobe started");
            }
            case "stop" -> {
                if (window == null) {
                    sender.sendMessage("benchprobe was not started");
                    return true;
                }
                Result result = window.close(barrelContents());
                window = null;
                sender.sendMessage(write(result) ? "benchprobe wrote " + RESULT_FILE : "benchprobe failed to write " + RESULT_FILE);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Items in every loaded barrel, by material. The benchmark's cells all empty into barrels, so the change is the work done. */
    private Map<String, Integer> barrelContents() {
        Map<String, Integer> totals = new TreeMap<>();
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof Barrel barrel)) continue;
                    for (ItemStack item : barrel.getSnapshotInventory().getContents()) {
                        if (item != null) totals.merge(item.getType().name(), item.getAmount(), Integer::sum);
                    }
                }
            }
        }
        return totals;
    }

    private boolean write(Result result) {
        try {
            Path file = getDataFolder().toPath().resolve(RESULT_FILE);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            getSLF4JLogger().error("Writing {} failed", RESULT_FILE, e);
            return false;
        }
    }
}
