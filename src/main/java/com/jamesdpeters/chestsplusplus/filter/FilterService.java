package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
 * Hopper filters at runtime (plan §5.5): {@code FilterIndex} (position → compiled filters, built from hopper PDC on
 * chunk load and on edit, dropped on unload/break) plus the small filter displays on the hopper's sides.
 */
public final class FilterService {

    private final Plugin plugin;
    private final FilterCodec codec;
    private final ItemGrouping grouping;
    private final Supplier<Settings> settings;
    private final NamespacedKey marker;
    private final Map<UUID, Map<Long, CompiledFilter>> index = new HashMap<>();
    private final Map<BlockPos, List<Entity>> displays = new HashMap<>();

    public FilterService(Plugin plugin, FilterCodec codec, ItemGrouping grouping, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.codec = codec;
        this.grouping = grouping;
        this.settings = settings;
        this.marker = new NamespacedKey(plugin, "filter_display");
    }

    public ItemGrouping grouping() {
        return grouping;
    }

    /** Hot path: the compiled filter for a hopper position, or null when it has none. */
    public @Nullable CompiledFilter get(UUID world, int x, int y, int z) {
        Map<Long, CompiledFilter> filters = index.get(world);
        return filters == null ? null : filters.get(BlockPos.packed(x, y, z));
    }

    public @Nullable CompiledFilter get(Block block) {
        return get(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /** The stored filters of a hopper (reads its PDC; for the editor). */
    public List<HopperFilter> read(Block hopper) {
        return hopper.getState(false) instanceof Hopper state
                ? codec.read(state.getPersistentDataContainer())
                : List.of();
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
        Map<Long, CompiledFilter> filters = index.get(chunk.getWorld().getUID());
        if (filters == null || filters.isEmpty()) return;
        List<BlockPos> inChunk = new ArrayList<>();
        UUID world = chunk.getWorld().getUID();
        for (long packed : filters.keySet()) {
            BlockPos pos = BlockPos.unpack(world, packed);
            if (pos.chunkX() == chunk.getX() && pos.chunkZ() == chunk.getZ()) inChunk.add(pos);
        }
        for (BlockPos pos : inChunk) {
            filters.remove(pos.packed());
            despawn(pos);
        }
    }

    /** The hopper is gone (broken, exploded). Its filters are lost in 3.0 (plan §12, default 4). */
    public void removed(Block block) {
        Map<Long, CompiledFilter> filters = index.get(block.getWorld().getUID());
        if (filters != null) filters.remove(BlockPos.packed(block.getX(), block.getY(), block.getZ()));
        despawn(BlockPos.of(block));
    }

    public void scanLoadedChunks() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) chunkLoaded(chunk);
        }
    }

    public void refreshDisplays() {
        List<BlockPos> positions = new ArrayList<>();
        index.forEach((world, filters) -> filters.keySet().forEach(p -> positions.add(BlockPos.unpack(world, p))));
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
        int count = 0;
        for (Map<Long, CompiledFilter> filters : index.values()) count += filters.size();
        return count;
    }

    public int displayCount() {
        int count = 0;
        for (List<Entity> entities : displays.values()) count += entities.size();
        return count;
    }

    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(marker, PersistentDataType.BOOLEAN);
    }

    private void index(Block hopper, List<HopperFilter> filters) {
        UUID world = hopper.getWorld().getUID();
        long key = BlockPos.packed(hopper.getX(), hopper.getY(), hopper.getZ());
        if (filters.isEmpty()) {
            Map<Long, CompiledFilter> map = index.get(world);
            if (map != null) map.remove(key);
        } else {
            index.computeIfAbsent(world, w -> new HashMap<>()).put(key, new CompiledFilter(filters, grouping));
        }
        despawn(BlockPos.of(hopper));
        if (!filters.isEmpty()) spawn(hopper, filters);
    }

    /**
     * On every side of the hopper: one row per non-empty mode (Allow first), each starting with a green/red pane and
     * followed by that mode's entries. Icons use the GUI transform, so blocks look like their inventory icons, and are
     * flattened onto the face.
     */
    private void spawn(Block hopper, List<HopperFilter> filters) {
        if (!settings.get().filters().displays()) return;
        List<List<ItemStack>> rows = new ArrayList<>();
        for (HopperFilter.Mode mode : HopperFilter.Mode.values()) {
            List<ItemStack> row = new ArrayList<>();
            for (HopperFilter filter : filters) if (filter.mode() == mode) row.add(filter.template());
            if (row.isEmpty()) continue;
            row.addFirst(ItemStack.of(
                    mode == HopperFilter.Mode.ALLOW
                            ? Material.LIME_STAINED_GLASS_PANE
                            : Material.RED_STAINED_GLASS_PANE));
            rows.add(row.subList(0, Math.min(row.size(), DisplayLayout.FILTER_COLUMNS)));
        }
        List<Entity> spawned = new ArrayList<>();
        World world = hopper.getWorld();
        for (BlockFace face : DisplayLayout.HORIZONTAL) {
            for (int r = 0; r < Math.min(rows.size(), DisplayLayout.FILTER_ROWS); r++) {
                List<ItemStack> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    ItemStack icon = row.get(c);
                    DisplayLayout.Placement placement = DisplayLayout.filterCell(face, r, c);
                    Location at = hopper.getLocation().clone().add(placement.x(), placement.y(), placement.z());
                    at.setYaw(placement.yaw());
                    spawned.add(world.spawn(at, ItemDisplay.class, entity -> {
                        prepare(entity);
                        entity.setItemStack(icon);
                        entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                        float scale = DisplayLayout.FILTER_ITEM_SCALE;
                        entity.setTransformation(new Transformation(
                                new Vector3f(),
                                new AxisAngle4f(),
                                new Vector3f(scale, scale, 0.001f),
                                new AxisAngle4f()));
                    }));
                }
            }
        }
        displays.put(BlockPos.of(hopper), spawned);
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
     * With Paper's {@code hopper.disable-move-event: true} no InventoryMoveItemEvent fires, so filters can't work
     * (spike S1). Reads the Paper world config files and returns the affected world names.
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
