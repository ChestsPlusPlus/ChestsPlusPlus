package com.jamesdpeters.chestsplusplus.chestlink.sort;

import com.jamesdpeters.chestsplusplus.model.SortMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Condenses similar stacks without increasing their slot count, then orders them in O(n log n). */
public final class Sorter {

    private Sorter() {}

    private record Pile(ItemStack template, String key, long total, int largest, int firstSlot) {

        Pile add(int amount) {
            return new Pile(template, key, total + amount, Math.max(largest, amount), firstSlot);
        }
    }

    /** Returns the sorted contents for an inventory of {@code size} slots (nulls for empty slots). */
    public static @Nullable ItemStack[] sort(@Nullable ItemStack[] contents, SortMode mode, int size) {
        if (mode == SortMode.OFF) return contents;
        // Group similar stacks; LinkedHashMap keeps first-seen order for stable ties.
        Map<ItemStack, Pile> grouped = new LinkedHashMap<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.isEmpty()) continue;
            ItemStack template = item.asOne();
            Pile pile = grouped.get(template);
            grouped.put(template, pile == null
                    ? new Pile(template, sortKey(template), item.getAmount(), item.getAmount(), slot)
                    : pile.add(item.getAmount()));
        }
        List<Pile> piles = new ArrayList<>(grouped.values());

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
            // Keep oversized amounts so condensing never needs more slots than the original stacks.
            int max = Math.max(pile.largest(), Math.max(1, pile.template().getMaxStackSize()));
            long remaining = pile.total();
            while (remaining > 0) {
                if (slot >= size) return contents;
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
