package com.jamesdpeters.chestsplusplus.filter;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A hopper's filters prepared for the hot path (plan §5.5). Semantics (same as v2): any deny match rejects; if any
 * allow entries exist an item must match at least one; with no entries everything passes.
 */
public final class CompiledFilter {

    private record Entry(Material type, HopperFilter.Match match, ItemStack template) {}

    private final List<HopperFilter> filters;
    private final Entry[] allows;
    private final Entry[] denies;
    private final ItemGrouping grouping;

    public CompiledFilter(List<HopperFilter> filters, ItemGrouping grouping) {
        this.filters = List.copyOf(filters);
        this.grouping = grouping;
        this.allows = filters.stream()
                .filter(f -> f.mode() == HopperFilter.Mode.ALLOW)
                .map(f -> new Entry(f.template().getType(), f.match(), f.template()))
                .toArray(Entry[]::new);
        this.denies = filters.stream()
                .filter(f -> f.mode() == HopperFilter.Mode.DENY)
                .map(f -> new Entry(f.template().getType(), f.match(), f.template()))
                .toArray(Entry[]::new);
    }

    public List<HopperFilter> filters() {
        return filters;
    }

    public boolean isEmpty() {
        return filters.isEmpty();
    }

    public boolean accepts(ItemStack item) {
        Material type = item.getType();
        for (Entry deny : denies) if (matches(deny, type, item)) return false;
        if (allows.length == 0) return true;
        for (Entry allow : allows) if (matches(allow, type, item)) return true;
        return false;
    }

    private boolean matches(Entry entry, Material type, ItemStack item) {
        return switch (entry.match()) {
            case TYPE -> entry.type() == type;
            case SIMILAR -> grouping.similar(entry.type(), type);
            case EXACT -> entry.type() == type && entry.template().isSimilar(item);
        };
    }
}
