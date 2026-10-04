package com.jamesdpeters.chestsplusplus.autocraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Plans one craft: picks an input stack for every recipe slot without copying inventories, then checks
 * output capacity by scanning slots. Committing the plan is a separate, atomic step.
 */
public final class CraftPlanner {

    private CraftPlanner() {}

    /** Where one matrix slot's ingredient comes from. */
    public record Source(Inventory inventory, int slot) {}

    /** A complete extraction plan: per matrix slot its source (null for empty slots) and the planned matrix. */
    public record Plan(List<@Nullable Source> sources, @Nullable ItemStack[] matrix) {}

    /**
     * Greedily assigns an input stack to each non-empty recipe slot, counting what each stack has already given so a
     * stack of 1 isn't used twice. Returns null when some slot can't be filled.
     */
    public static @Nullable Plan plan(List<@Nullable Predicate<ItemStack>> slots, List<Inventory> inputs) {
        Map<Source, Integer> used = new HashMap<>();
        List<@Nullable Source> sources = new ArrayList<>(9);
        @Nullable ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Predicate<ItemStack> wants = i < slots.size() ? slots.get(i) : null;
            if (wants == null) {
                sources.add(null);
                continue;
            }
            Source found = findSource(wants, inputs, used);
            if (found == null) return null;
            used.merge(found, 1, Integer::sum);
            sources.add(found);
            matrix[i] = Objects.requireNonNull(found.inventory().getItem(found.slot())).asOne();
        }
        return new Plan(sources, matrix);
    }

    /** The first input stack that {@code wants} accepts and that still has an item not already planned. */
    private static @Nullable Source findSource(Predicate<ItemStack> wants, List<Inventory> inputs, Map<Source, Integer> used) {
        for (Inventory inventory : inputs) {
            ItemStack[] contents = inventory.getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item == null || item.isEmpty() || !wants.test(item)) continue;
                Source source = new Source(inventory, slot);
                if (used.getOrDefault(source, 0) < item.getAmount()) return source;
            }
        }
        return null;
    }

    /** Can {@code output} take all of {@code items}? Scans slots once per item type; no inventory copies. */
    public static boolean fits(Inventory output, List<ItemStack> items) {
        ItemStack[] contents = output.getStorageContents();
        int[] space = new int[contents.length];
        boolean[] empty = new boolean[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            empty[i] = item == null || item.isEmpty();
            space[i] = empty[i] ? 0 : Math.max(0, item.getMaxStackSize() - item.getAmount());
        }
        for (ItemStack wanted : items) {
            int remaining = wanted.getAmount();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (!empty[i] && space[i] > 0 && contents[i].isSimilar(wanted)) {
                    int take = Math.min(space[i], remaining);
                    space[i] -= take;
                    remaining -= take;
                }
            }
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (empty[i]) {
                    empty[i] = false;
                    contents[i] = wanted;
                    int take = Math.min(wanted.getMaxStackSize(), remaining);
                    space[i] = wanted.getMaxStackSize() - take;
                    remaining -= take;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    /** Takes one item from each planned source. Call only after {@link #fits} succeeded. */
    public static void commit(Plan plan) {
        for (Source source : plan.sources()) {
            if (source == null) continue;
            ItemStack item = source.inventory().getItem(source.slot());
            if (item == null) continue;
            item.setAmount(item.getAmount() - 1);
            source.inventory().setItem(source.slot(), item.isEmpty() ? null : item);
        }
    }
}
