package com.jamesdpeters.chestsplusplus.chestlink.sort;

import com.jamesdpeters.chestsplusplus.model.SortMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Condensing sort in O(n log n): similar stacks are merged up to their max stack size, then ordered by
 * the mode. Replaces v2's O(n²) {@code isSimilar} comparators.
 */
public final class Sorter {

    private Sorter() {}

    /** A group of similar items: the template (amount 1) and the total amount. */
    private record Pile(ItemStack template, String key, long total, int firstSlot) {}

    /** Returns the sorted contents for an inventory of {@code size} slots (nulls for empty slots). */
    public static @Nullable ItemStack[] sort(@Nullable ItemStack[] contents, SortMode mode, int size) {
        if (mode == SortMode.OFF) return contents;
        // Group similar stacks; LinkedHashMap keeps first-seen order for stable ties.
        Map<ItemStack, long[]> totals = new LinkedHashMap<>();
        Map<ItemStack, Integer> firstSlots = new LinkedHashMap<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.isEmpty()) continue;
            ItemStack template = item.asOne();
            totals.computeIfAbsent(template, k -> new long[1])[0] += item.getAmount();
            firstSlots.putIfAbsent(template, slot);
        }
        List<Pile> piles = new ArrayList<>(totals.size());
        totals.forEach((template, total) -> piles
                .add(new Pile(template, sortKey(template), total[0], firstSlots.getOrDefault(template, Integer.MAX_VALUE))));

        Comparator<Pile> byName = Comparator.comparing(Pile::key);
        Comparator<Pile> order = switch (mode) {
            case NAME -> byName;
            case AMOUNT_ASC -> Comparator.comparingLong(Pile::total).thenComparing(byName);
            case AMOUNT_DESC -> Comparator.comparingLong(Pile::total).reversed().thenComparing(byName);
            case OFF -> Comparator.comparingInt(Pile::firstSlot);
        };
        piles.sort(order);

        @Nullable ItemStack[] out = new ItemStack[size];
        int slot = 0;
        for (Pile pile : piles) {
            int max = Math.max(1, pile.template().getMaxStackSize());
            long remaining = pile.total();
            while (remaining > 0 && slot < size) {
                int amount = (int) Math.min(max, remaining);
                out[slot++] = pile.template().asQuantity(amount);
                remaining -= amount;
            }
        }
        return out;
    }

    /** Material key, then a stable tiebreaker for differently-named/enchanted variants. */
    private static String sortKey(ItemStack item) {
        return item.getType().getKey().asString() + '\u0000' + item.hashCode();
    }
}
