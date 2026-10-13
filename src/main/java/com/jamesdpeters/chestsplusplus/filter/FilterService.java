package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Hopper filters at runtime: reads and writes them in the hopper's PDC, and keeps the {@link FilterIndex} and {@link FilterDisplays} in
 * step with the loaded chunks and with edits.
 */
public final class FilterService {

    private final Plugin plugin;
    private final FilterCodec codec;
    @Getter private final ItemGrouping grouping;
    private final Supplier<Settings> settings;
    @Getter private final FilterIndex index;
    @Getter private final FilterDisplays displays;
    /** Whether the index was built from the loaded chunks, which a reload that switches hopper filters on has to do. */
    private boolean scanned;

    public FilterService(Plugin plugin, FilterCodec codec, ItemGrouping grouping, Supplier<Settings> settings, FilterIndex index,
            FilterDisplays displays) {
        this.plugin = plugin;
        this.codec = codec;
        this.grouping = grouping;
        this.settings = settings;
        this.index = index;
        this.displays = displays;
    }

    /** The stored filters of a hopper (reads its PDC; for the editor). */
    public List<HopperFilter> read(Block hopper) {
        return hopper.getState(false) instanceof Hopper state ? codec.read(state.getPersistentDataContainer()) : List.of();
    }

    /** Saves filters to the hopper's PDC and, while hopper filters are on, refreshes the index and displays. */
    public void write(Block hopper, List<HopperFilter> filters) {
        // Snapshot + update (edits are rare): works whether or not the platform supports live block states.
        if (!(hopper.getState() instanceof Hopper state)) return;
        codec.write(state.getPersistentDataContainer(), filters);
        state.update(false, false);
        if (enabled()) index(hopper, filters);
    }

    /** Indexes the hoppers with filters in a just-loaded chunk and spawns their displays. */
    public void chunkLoaded(Chunk chunk) {
        if (!enabled()) return;
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.HOPPER, false)) {
            if (state instanceof Hopper hopper && codec.has(hopper.getPersistentDataContainer())) {
                index(state.getBlock(), codec.read(hopper.getPersistentDataContainer()));
            }
        }
    }

    public void chunkUnloaded(Chunk chunk) {
        index.removeChunk(chunk).forEach(displays::despawn);
    }

    /** The hopper is gone (broken, exploded), and its filters with it. */
    public void removed(Block block) {
        index.remove(block);
        displays.despawn(BlockPos.of(block));
    }

    /** Indexes the hoppers in every loaded chunk, unless hopper filters are off. */
    public void scanLoadedChunks() {
        scanned = enabled();
        if (!scanned) return;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) chunkLoaded(chunk);
        }
    }

    /** Applies reloaded settings: switching hopper filters off drops the index and displays, on builds them, otherwise displays respawn. */
    public void reload() {
        if (enabled()) {
            if (scanned) displays.refresh(index);
            else scanLoadedChunks();
            return;
        }
        displays.despawnAll();
        index.clear();
        scanned = false;
    }

    private boolean enabled() {
        return settings.get().features().hopperFilters();
    }

    private void index(Block hopper, List<HopperFilter> filters) {
        displays.despawn(BlockPos.of(hopper));
        if (filters.isEmpty()) {
            removed(hopper);
            return;
        }
        index.put(hopper, new CompiledFilter(filters, grouping));
        displays.spawn(hopper, filters);
    }

    /**
     * With Paper's {@code hopper.disable-move-event: true} no InventoryMoveItemEvent fires, so filters can't work.
     * Reads the Paper world config files and returns the affected world names.
     */
    public static List<String> worldsWithMoveEventDisabled(File serverRoot, List<World> worlds) {
        boolean defaultDisabled = disabledIn(new File(serverRoot, "config/paper-world-defaults.yml"), false);
        List<String> affected = new ArrayList<>();
        for (World world : worlds) {
            File perWorld = new File(world.getWorldFolder(), "paper-world.yml");
            if (disabledIn(perWorld, defaultDisabled)) affected.add(world.getName());
        }
        return affected;
    }

    private static boolean disabledIn(File file, boolean fallback) {
        if (!file.isFile()) return fallback;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Object value = yaml.get("hopper.disable-move-event");
        return value instanceof Boolean b ? b : fallback;
    }
}
