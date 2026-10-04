package com.jamesdpeters.chestsplusplus.filter;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * "Hover" info for filter displays (display entities can't have tooltips): when a player looks at a filtered hopper,
 * the action bar shows the entry under the crosshair (allowed/denied, item, match), or a summary between entries.
 * Runs from a central ticker, only while some hopper has filters, with one short ray trace per player.
 */
public final class FilterHover {

    static final double REACH = 5.0;
    /** Re-send an unchanged action bar this often so it doesn't fade while the player keeps looking. */
    static final int REFRESH_TICKS = 30;

    private record Shown(String key, int tick) {}

    private final Services services;
    private final FilterService filters;
    private final Map<UUID, Shown> shown = new HashMap<>();
    private int tick;

    public FilterHover(Services services, FilterService filters) {
        this.services = services;
        this.filters = filters;
    }

    public void tick(int intervalTicks) {
        tick += intervalTicks;
        if (filters.indexedCount() == 0 || !services.settings().features().hopperFilters()) {
            shown.clear();
            return;
        }
        var online = services.plugin().getServer().getOnlinePlayers();
        if (shown.size() > online.size()) {
            shown.keySet().removeIf(id -> services.plugin().getServer().getPlayer(id) == null);
        }
        for (Player player : online) update(player);
    }

    private void update(Player player) {
        RayTraceResult hit = player.rayTraceBlocks(REACH, FluidCollisionMode.NEVER);
        Block block = hit == null ? null : hit.getHitBlock();
        CompiledFilter filter = block == null || block.getType() != Material.HOPPER ? null : filters.get(block);
        if (hit == null || block == null || filter == null) {
            shown.remove(player.getUniqueId());
            return;
        }
        List<HopperFilter> entries = filter.filters();
        int index = -1;
        BlockFace face = hit.getHitBlockFace();
        if (face != null) {
            Vector local = hit.getHitPosition().subtract(block.getLocation().toVector());
            index = DisplayLayout.filterCellAt(face, local.getX(), local.getY(), local.getZ());
        }
        String key =
                block.getX() + "," + block.getY() + "," + block.getZ() + "#" + (index < entries.size() ? index : -1);
        Shown last = shown.get(player.getUniqueId());
        if (last != null && last.key().equals(key) && tick - last.tick() < REFRESH_TICKS) return;
        shown.put(player.getUniqueId(), new Shown(key, tick));
        player.sendActionBar(index >= 0 && index < entries.size() ? describe(entries.get(index)) : summary(entries));
    }

    Component describe(HopperFilter entry) {
        var messages = services.messages();
        Message match = switch (entry.match()) {
            case EXACT -> Message.FILTER_MATCH_EXACT;
            case TYPE -> Message.FILTER_MATCH_TYPE;
            case SIMILAR -> Message.FILTER_MATCH_SIMILAR;
        };
        return messages.get(
                Message.FILTER_HOVER,
                Messages.component(
                        "mode",
                        messages.get(
                                entry.mode() == HopperFilter.Mode.ALLOW ? Message.FILTER_ALLOW : Message.FILTER_DENY)),
                Messages.component("item", name(entry.template())),
                Messages.component("match", messages.get(match)));
    }

    /** The item's custom name if it has one, else its translated name (rendered in the player's language). */
    private static Component name(org.bukkit.inventory.ItemStack item) {
        Component custom = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_NAME);
        return custom != null ? custom : Component.translatable(item.translationKey());
    }

    Component summary(List<HopperFilter> entries) {
        long allowed = entries.stream()
                .filter(e -> e.mode() == HopperFilter.Mode.ALLOW)
                .count();
        return services.messages()
                .get(
                        Message.FILTER_HOVER_SUMMARY,
                        Messages.text("allowed", Long.toString(allowed)),
                        Messages.text("denied", Long.toString(entries.size() - allowed)));
    }
}
