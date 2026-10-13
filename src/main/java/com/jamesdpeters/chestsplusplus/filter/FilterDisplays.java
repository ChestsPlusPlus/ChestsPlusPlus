package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout;
import com.jamesdpeters.chestsplusplus.display.MarkerDisplays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/** The small icons on each side of a filtered hopper that show its filters. */
@RequiredArgsConstructor
public final class FilterDisplays {

    private static final float VIEW_RANGE = 0.3f;

    private final MarkerDisplays markers;
    private final Supplier<Settings> settings;
    private final Map<BlockPos, List<Entity>> displays = new HashMap<>();

    /**
     * On every side of the hopper: one row per non-empty mode (Allow first), each starting with a green/red pane and
     * followed by that mode's entries. Icons use the GUI transform, so blocks look like their inventory icons, and are
     * flattened onto the face.
     */
    void spawn(Block hopper, List<HopperFilter> filters) {
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
        float scale = DisplayLayout.FILTER_ITEM_SCALE;
        return markers.spawn(hopper, placement, ItemDisplay.class, VIEW_RANGE, entity -> {
            entity.setItemStack(icon);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            entity.setTransformation(MarkerDisplays.scale(scale, scale, 0.001f));
        });
    }

    void despawn(BlockPos pos) {
        List<Entity> entities = displays.remove(pos);
        if (entities != null) entities.forEach(Entity::remove);
    }

    public void despawnAll() {
        displays.values().forEach(entities -> entities.forEach(Entity::remove));
        displays.clear();
    }

    /** Respawns the displays of every indexed hopper, as the settings may have changed. */
    void refresh(FilterIndex index) {
        despawnAll();
        index.forEach((pos, filter) -> {
            Block block = pos.block();
            if (block != null) spawn(block, filter.filters());
        });
    }

    public int displayCount() {
        return displays.values().stream().mapToInt(List::size).sum();
    }

    public boolean isOurs(Entity entity) {
        return markers.isOurs(entity);
    }
}
