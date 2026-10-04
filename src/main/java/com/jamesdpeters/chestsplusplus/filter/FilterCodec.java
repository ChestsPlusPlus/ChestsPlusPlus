package com.jamesdpeters.chestsplusplus.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Stores hopper filters in the hopper's PDC (plan §4.4, §5.5): a version number and a list of
 * {@code (item bytes, mode, match)} containers. Filters travel with the block (schematics, WorldEdit).
 */
public final class FilterCodec {

    public static final int VERSION = 1;

    private final NamespacedKey versionKey;
    private final NamespacedKey entriesKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey matchKey;

    public FilterCodec(Plugin plugin) {
        versionKey = new NamespacedKey(plugin, "filter_version");
        entriesKey = new NamespacedKey(plugin, "filters");
        itemKey = new NamespacedKey(plugin, "item");
        modeKey = new NamespacedKey(plugin, "mode");
        matchKey = new NamespacedKey(plugin, "match");
    }

    public boolean has(PersistentDataContainer pdc) {
        return pdc.has(entriesKey);
    }

    public List<HopperFilter> read(PersistentDataContainer pdc) {
        List<PersistentDataContainer> entries = pdc.get(entriesKey, PersistentDataType.LIST.dataContainers());
        if (entries == null) return List.of();
        List<HopperFilter> filters = new ArrayList<>(entries.size());
        for (PersistentDataContainer entry : entries) {
            byte[] bytes = entry.get(itemKey, PersistentDataType.BYTE_ARRAY);
            String mode = entry.get(modeKey, PersistentDataType.STRING);
            String match = entry.get(matchKey, PersistentDataType.STRING);
            if (bytes == null || mode == null || match == null) continue;
            try {
                ItemStack item = ItemStack.deserializeBytes(bytes);
                if (item.isEmpty()) continue;
                filters.add(new HopperFilter(item, HopperFilter.Mode.valueOf(mode.toUpperCase(Locale.ROOT)),
                        HopperFilter.Match.valueOf(match.toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException ignored) {
                // corrupt or unknown entry: skip it rather than losing the rest
            }
        }
        return filters;
    }

    /** Writes the filters; an empty list removes the keys entirely. */
    public void write(PersistentDataContainer pdc, List<HopperFilter> filters) {
        if (filters.isEmpty()) {
            pdc.remove(entriesKey);
            pdc.remove(versionKey);
            return;
        }
        PersistentDataAdapterContext context = pdc.getAdapterContext();
        List<PersistentDataContainer> entries = new ArrayList<>(filters.size());
        for (HopperFilter filter : filters) {
            PersistentDataContainer entry = context.newPersistentDataContainer();
            entry.set(itemKey, PersistentDataType.BYTE_ARRAY, filter.template().serializeAsBytes());
            entry.set(modeKey, PersistentDataType.STRING, filter.mode().name());
            entry.set(matchKey, PersistentDataType.STRING, filter.match().name());
            entries.add(entry);
        }
        pdc.set(versionKey, PersistentDataType.INTEGER, VERSION);
        pdc.set(entriesKey, PersistentDataType.LIST.dataContainers(), entries);
    }
}
