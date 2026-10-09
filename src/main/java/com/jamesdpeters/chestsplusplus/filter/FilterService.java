package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Hopper filters at runtime: an index (world → chunk → position → compiled filters, built from hopper PDC on
 * chunk load and on edit, dropped on unload/break) plus the small filter displays on the hopper's sides.
 */
public final class FilterService {

    private final Plugin plugin;
    private final FilterCodec codec;
    @Getter private final ItemGrouping grouping;
    private final Supplier<Settings> settings;
    private final NamespacedKey marker;
    private final Map<UUID, Long2ObjectMap<Long2ObjectMap<CompiledFilter>>> index = new HashMap<>();
    private final Map<BlockPos, List<Entity>> displays = new HashMap<>();

    public FilterService(Plugin plugin, FilterCodec codec, ItemGrouping grouping, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.codec = codec;
        this.grouping = grouping;
        this.settings = settings;
        this.marker = new NamespacedKey(plugin, "filter_display");
    }

    /** Hot path: the compiled filter for a hopper position, or null when it has none. */
    public @Nullable CompiledFilter get(UUID world, int x, int y, int z) {
        Long2ObjectMap<CompiledFilter> filters = inChunk(world, BlockPos.chunkKey(x >> 4, z >> 4));
        return filters == null ? null : filters.get(BlockPos.packed(x, y, z));
    }

    public @Nullable CompiledFilter get(Block block) {
        return get(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /** The stored filters of a hopper (reads its PDC; for the editor). */
    public List<HopperFilter> read(Block hopper) {
        return hopper.getState(false) instanceof Hopper state ? codec.read(state.getPersistentDataContainer()) : List.of();
    }

    /** Saves filters to the hopper's PDC and refreshes the index and displays. */
    public void write(Block hopper, List<HopperFilter> filters) {
        // Snapshot + update (edits are rare): works whether or not the platform supports live block states.
        if (!(hopper.getState() instanceof Hopper state)) return;
        codec.write(state.getPersistentDataContainer(), filters);
        state.update(false, false);
        index(hopper, filters);
    }

    /** Indexes the hoppers with filters in a just-loaded chunk and spawns their displays. */
    public void chunkLoaded(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.HOPPER, false)) {
            if (state instanceof Hopper hopper && codec.has(hopper.getPersistentDataContainer())) {
                index(state.getBlock(), codec.read(hopper.getPersistentDataContainer()));
            }
        }
    }

    public void chunkUnloaded(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        Long2ObjectMap<Long2ObjectMap<CompiledFilter>> chunks = index.get(world);
        Long2ObjectMap<CompiledFilter> filters = chunks == null ? null : chunks.remove(BlockPos.chunkKey(chunk.getX(), chunk.getZ()));
        if (filters != null) filters.keySet().forEach((long packed) -> despawn(BlockPos.unpack(world, packed)));
    }

    /** The hopper is gone (broken, exploded), and its filters with it. */
    public void removed(Block block) {
        long chunkKey = BlockPos.chunkKey(block.getX() >> 4, block.getZ() >> 4);
        Long2ObjectMap<Long2ObjectMap<CompiledFilter>> chunks = index.get(block.getWorld().getUID());
        Long2ObjectMap<CompiledFilter> filters = chunks == null ? null : chunks.get(chunkKey);
        if (filters != null) {
            filters.remove(BlockPos.packed(block.getX(), block.getY(), block.getZ()));
            if (filters.isEmpty()) chunks.remove(chunkKey);
        }
        despawn(BlockPos.of(block));
    }

    public void scanLoadedChunks() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) chunkLoaded(chunk);
        }
    }

    public void refreshDisplays() {
        List<BlockPos> positions = new ArrayList<>();
        index.forEach((world, chunks) -> chunks.values()
                .forEach(filters -> filters.keySet().forEach((long p) -> positions.add(BlockPos.unpack(world, p)))));
        despawnAll();
        for (BlockPos pos : positions) {
            Block block = pos.block();
            CompiledFilter filter = get(pos.world(), pos.x(), pos.y(), pos.z());
            if (block != null && filter != null) spawn(block, filter.filters());
        }
    }

    public void despawnAll() {
        displays.values().forEach(entities -> entities.forEach(Entity::remove));
        displays.clear();
    }

    public int indexedCount() {
        return index.values().stream().flatMap(chunks -> chunks.values().stream()).mapToInt(Map::size).sum();
    }

    public int displayCount() {
        return displays.values().stream().mapToInt(List::size).sum();
    }

    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(marker, PersistentDataType.BOOLEAN);
    }

    private void index(Block hopper, List<HopperFilter> filters) {
        despawn(BlockPos.of(hopper));
        if (filters.isEmpty()) {
            removed(hopper);
            return;
        }
        long key = BlockPos.packed(hopper.getX(), hopper.getY(), hopper.getZ());
        long chunkKey = BlockPos.chunkKey(hopper.getX() >> 4, hopper.getZ() >> 4);
        index.computeIfAbsent(hopper.getWorld().getUID(), w -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(chunkKey, k -> new Long2ObjectOpenHashMap<>())
                .put(key, new CompiledFilter(filters, grouping));
        spawn(hopper, filters);
    }

    private @Nullable Long2ObjectMap<CompiledFilter> inChunk(UUID world, long chunkKey) {
        Long2ObjectMap<Long2ObjectMap<CompiledFilter>> chunks = index.get(world);
        return chunks == null ? null : chunks.get(chunkKey);
    }

    /**
     * On every side of the hopper: one row per non-empty mode (Allow first), each starting with a green/red pane and
     * followed by that mode's entries. Icons use the GUI transform, so blocks look like their inventory icons, and are
     * flattened onto the face.
     */
    private void spawn(Block hopper, List<HopperFilter> filters) {
        if (!settings.get().filters().displays()) return;
        List<List<ItemStack>> rows = iconRows(filters);
        List<Entity> spawned = new ArrayList<>();
        for (BlockFace face : DisplayLayout.HORIZONTAL) {
            for (int r = 0; r < Math.min(rows.size(), DisplayLayout.FILTER_ROWS); r++) {
                List<ItemStack> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) spawned.add(spawnIcon(hopper, DisplayLayout.filterCell(face, r, c), row.get(c)));
            }
        }
        displays.put(BlockPos.of(hopper), spawned);
    }

    /** One row per non-empty mode, Allow first: a green or red pane, then that mode's entries. */
    private static List<List<ItemStack>> iconRows(List<HopperFilter> filters) {
        List<List<ItemStack>> rows = new ArrayList<>();
        for (HopperFilter.Mode mode : HopperFilter.Mode.values()) {
            List<ItemStack> row = new ArrayList<>(filters.stream().filter(filter -> filter.mode() == mode).map(HopperFilter::template).toList());
            if (row.isEmpty()) continue;
            row.addFirst(ItemStack.of(mode == HopperFilter.Mode.ALLOW ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE));
            rows.add(row.subList(0, Math.min(row.size(), DisplayLayout.FILTER_COLUMNS)));
        }
        return rows;
    }

    private Entity spawnIcon(Block hopper, DisplayLayout.Placement placement, ItemStack icon) {
        Location at = hopper.getLocation().clone().add(placement.x(), placement.y(), placement.z());
        at.setYaw(placement.yaw());
        float scale = DisplayLayout.FILTER_ITEM_SCALE;
        return hopper.getWorld().spawn(at, ItemDisplay.class, entity -> {
            prepare(entity);
            entity.setItemStack(icon);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, 0.001f), new AxisAngle4f()));
        });
    }

    private void prepare(Display entity) {
        entity.setPersistent(false);
        entity.setViewRange(0.3f);
        entity.getPersistentDataContainer().set(marker, PersistentDataType.BOOLEAN, true);
    }

    private void despawn(BlockPos pos) {
        List<Entity> entities = displays.remove(pos);
        if (entities != null) entities.forEach(Entity::remove);
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
