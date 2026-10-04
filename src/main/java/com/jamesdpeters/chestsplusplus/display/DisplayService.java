package com.jamesdpeters.chestsplusplus.display;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Non-persistent {@link ItemDisplay}/{@link TextDisplay} views of nodes (plan §5.3). Spawned when a chunk loads (and
 * for loaded chunks at enable), dropped on unload; updates go through a debounced queue and only touch an entity when
 * the shown item actually changes. No per-player packets and no per-node tasks.
 */
public final class DisplayService {

    /** What a group shows: its item (null for nothing) and its label text. */
    public interface Content {
        @Nullable
        ItemStack item(StorageGroup group);

        Component label(StorageGroup group);
    }

    private record NodeDisplay(ItemDisplay item, @Nullable TextDisplay label, @Nullable ItemStack shown) {}

    private static final int UPDATE_INTERVAL_TICKS = 8;

    private final Plugin plugin;
    private final GroupRegistry groups;
    private final NodeIndex nodes;
    private final Supplier<Settings> settings;
    private final Map<GroupType, Content> contents = new HashMap<>();
    private final Map<BlockPos, NodeDisplay> displays = new HashMap<>();
    private final Set<Long> pendingGroups = new LinkedHashSet<>();
    private final Set<ChunkRef> pendingChunks = new LinkedHashSet<>();
    private final NamespacedKey marker;
    private Function<Block, DisplayLayout.Surface> surfaces = block -> DisplayLayout.Surface.FULL_BLOCK;
    private int tick;

    private record ChunkRef(UUID world, long key) {}

    public DisplayService(Plugin plugin, GroupRegistry groups, NodeIndex nodes, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.groups = groups;
        this.nodes = nodes;
        this.settings = settings;
        this.marker = new NamespacedKey(plugin, "display");
    }

    public void register(GroupType type, Content content) {
        contents.put(type, content);
    }

    public void surfaces(Function<Block, DisplayLayout.Surface> surfaces) {
        this.surfaces = surfaces;
    }

    /** Marks entities as ours, for cleanup and for tests. */
    public NamespacedKey marker() {
        return marker;
    }

    /** Queues a group's displays to be refreshed on the next update tick (debounced). */
    public void requestUpdate(StorageGroup group) {
        pendingGroups.add(group.id());
    }

    public void chunkLoaded(UUID world, long chunkKey) {
        pendingChunks.add(new ChunkRef(world, chunkKey));
    }

    public void chunkUnloaded(UUID world, long chunkKey) {
        pendingChunks.remove(new ChunkRef(world, chunkKey));
        for (Node node : nodes.inChunk(world, chunkKey)) despawn(node.pos());
    }

    public void nodeAdded(Node node) {
        if (node.pos().isLoaded()) spawn(node);
    }

    public void nodeRemoved(Node node) {
        despawn(node.pos());
    }

    /** Called every tick: spawns for newly loaded chunks; processes the update queue every few ticks. */
    public void tick() {
        if (!pendingChunks.isEmpty()) {
            List<ChunkRef> chunks = new ArrayList<>(pendingChunks);
            pendingChunks.clear();
            for (ChunkRef chunk : chunks) {
                for (Node node : nodes.inChunk(chunk.world(), chunk.key())) {
                    if (!displays.containsKey(node.pos()) && node.pos().isLoaded()) spawn(node);
                }
            }
        }
        if (++tick % UPDATE_INTERVAL_TICKS != 0 || pendingGroups.isEmpty()) return;
        List<Long> ids = new ArrayList<>(pendingGroups);
        pendingGroups.clear();
        for (long id : ids) {
            StorageGroup group = groups.byId(id);
            if (group != null) update(group);
        }
    }

    /** Despawns everything and respawns displays for all loaded nodes (enable, reload). */
    public void refreshAll() {
        despawnAll();
        for (Node node : nodes.all()) {
            if (node.pos().isLoaded()) spawn(node);
        }
    }

    public void despawnAll() {
        displays.values().forEach(DisplayService::remove);
        displays.clear();
    }

    public int count() {
        return displays.size();
    }

    private void update(StorageGroup group) {
        Content content = contents.get(group.type());
        if (content == null) return;
        @Nullable ItemStack item = normalise(content.item(group));
        Component label = content.label(group);
        for (Node node : nodes.nodesOf(group.id())) {
            NodeDisplay display = displays.get(node.pos());
            if (display == null) {
                if (node.pos().isLoaded()) spawn(node);
                continue;
            }
            if (!display.item().isValid()) {
                displays.remove(node.pos());
                spawn(node);
                continue;
            }
            if (DisplayLayout.shapeOf(display.shown()) != DisplayLayout.shapeOf(item)) {
                spawn(node); // block <-> flat item moves the display (see DisplayLayout#nodeItem)
                continue;
            }
            if (!Objects.equals(display.shown(), item)) {
                display.item().setItemStack(item);
                displays.put(node.pos(), new NodeDisplay(display.item(), display.label(), item));
            }
            if (display.label() != null && !label.equals(display.label().text())) display.label().text(label);
        }
    }

    private void spawn(Node node) {
        StorageGroup group = groups.byId(node.groupId());
        Content content = group == null ? null : contents.get(group.type());
        if (group == null || content == null) return;
        Settings.Display config = group.type().pick(settings.get().chestlink().display(), settings.get().autocraft().display());
        if (!config.enabled()) return;
        Block block = node.pos().block();
        if (block == null) return;
        despawn(node.pos());
        World world = block.getWorld();
        DisplayLayout.Surface surface = surfaces.apply(block);

        @Nullable ItemStack shown = normalise(content.item(group));
        DisplayLayout.Placement itemAt = DisplayLayout.nodeItem(surface, node.facing(), DisplayLayout.shapeOf(shown));
        ItemDisplay item = world.spawn(at(block, itemAt), ItemDisplay.class, entity -> {
            prepare(entity, config.viewRange());
            entity.setItemStack(shown);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(scale(DisplayLayout.NODE_ITEM_SCALE, DisplayLayout.NODE_ITEM_SCALE, DisplayLayout.NODE_ITEM_SCALE));
        });

        TextDisplay label = null;
        if (config.label()) {
            DisplayLayout.Placement labelAt = DisplayLayout.nodeLabel(surface, node.facing());
            Component text = content.label(group);
            label = world.spawn(at(block, labelAt), TextDisplay.class, entity -> {
                prepare(entity, config.viewRange() / 2);
                // Labels stay fully lit so names are readable at night; item displays use the world's lighting.
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.text(text);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(org.bukkit.Color.fromARGB(0x40000000));
                entity.setShadowed(true);
                entity.setLineWidth(200);
                entity.setTransformation(scale(0.35f, 0.35f, 0.35f));
            });
        }
        displays.put(node.pos(), new NodeDisplay(item, label, shown));
    }

    private void prepare(Display entity, float viewRange) {
        entity.setPersistent(false);
        entity.setViewRange(viewRange);
        entity.getPersistentDataContainer().set(marker, PersistentDataType.BOOLEAN, true);
    }

    private void despawn(BlockPos pos) {
        NodeDisplay display = displays.remove(pos);
        if (display != null) remove(display);
    }

    private static void remove(NodeDisplay display) {
        display.item().remove();
        if (display.label() != null) display.label().remove();
    }

    private static Location at(Block block, DisplayLayout.Placement placement) {
        Location location = block.getLocation().clone().add(placement.x(), placement.y(), placement.z());
        location.setYaw(placement.yaw());
        location.setPitch(0);
        return location;
    }

    static Transformation scale(float x, float y, float z) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(x, y, z), new AxisAngle4f());
    }

    /** Displays show a plain single item of the type, so meta differences don't cause entity updates. */
    private static @Nullable ItemStack normalise(@Nullable ItemStack item) {
        return item == null || item.isEmpty() ? null : ItemStack.of(item.getType());
    }

    /** Is this entity one of ours (by PDC marker)? */
    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(marker, PersistentDataType.BOOLEAN);
    }

    Plugin plugin() {
        return plugin;
    }
}
