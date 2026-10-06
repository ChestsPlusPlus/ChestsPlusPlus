package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.filter.HopperFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Converts v2 hopper filters (item frames on hoppers, the frame's rotation choosing the mode) into v3 filter entries. Each chunk is
 * scanned once and then marked in its PDC. The frames are removed and they and their items dropped, since the filter display replaces them.
 */
@RequiredArgsConstructor
public final class V2FilterMigration implements Listener {

    private final MigrationState state;
    private final FilterService filters;
    private final NamespacedKey scannedKey;
    /** Filter entries converted since startup, whichever path converted them. */
    @Getter private int converted;

    @EventHandler
    void onEntitiesLoad(EntitiesLoadEvent event) {
        if (state.filters() == MigrationState.Filters.ON_LOAD) convert(event.getChunk(), event.getEntities());
    }

    /** Converts the frames in a chunk not scanned before, returning how many filter entries were added. */
    public int convert(Chunk chunk, List<Entity> entities) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (pdc.has(scannedKey)) return 0;
        Map<Block, List<HopperFilter>> byHopper = new LinkedHashMap<>();
        for (Entity entity : entities) {
            if (!(entity instanceof ItemFrame frame) || frame.getItem().isEmpty()) continue;
            Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
            if (attached.getType() != Material.HOPPER) continue;
            byHopper.computeIfAbsent(attached, k -> new ArrayList<>()).add(filter(frame.getItem(), frame.getRotation()));
            dropAndRemove(frame);
        }
        int added = 0;
        for (Map.Entry<Block, List<HopperFilter>> entry : byHopper.entrySet()) added += append(entry.getKey(), entry.getValue());
        pdc.set(scannedKey, PersistentDataType.BOOLEAN, true);
        converted += added;
        return added;
    }

    private int append(Block hopper, List<HopperFilter> converted) {
        List<HopperFilter> all = new ArrayList<>(filters.read(hopper));
        int before = all.size();
        converted.stream().filter(filter -> !all.contains(filter)).forEach(all::add);
        if (all.size() > before) filters.write(hopper, all);
        return all.size() - before;
    }

    /** v2's rules: clockwise rejects, flipped or counter-clockwise matches by type and tags, anything else accepts the exact item. */
    static HopperFilter filter(ItemStack item, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE -> new HopperFilter(item, HopperFilter.Mode.DENY, HopperFilter.Match.EXACT);
            case COUNTER_CLOCKWISE -> new HopperFilter(item, HopperFilter.Mode.DENY, HopperFilter.Match.SIMILAR);
            case FLIPPED -> new HopperFilter(item, HopperFilter.Mode.ALLOW, HopperFilter.Match.SIMILAR);
            default -> new HopperFilter(item, HopperFilter.Mode.ALLOW, HopperFilter.Match.EXACT);
        };
    }

    private static void dropAndRemove(ItemFrame frame) {
        Location at = frame.getLocation();
        at.getWorld().dropItemNaturally(at, frame.getItem());
        at.getWorld().dropItemNaturally(at, new ItemStack(frame instanceof GlowItemFrame ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME));
        frame.remove();
    }
}
