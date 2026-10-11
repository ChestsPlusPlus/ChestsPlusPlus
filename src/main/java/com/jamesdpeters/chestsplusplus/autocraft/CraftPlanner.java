package com.jamesdpeters.chestsplusplus.autocraft;

import java.util.ArrayList;
import java.util.List;
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
    public record Plan(List<@Nullable Source> sources, @Nullable ItemStack[] matrix) {

        /** The inventories the plan takes from, each once. */
        public List<Inventory> inventories() {
            return sources.stream().filter(Objects::nonNull).map(Source::inventory).distinct().toList();
        }
    }

    /**
     * Assigns an input stack to each non-empty recipe slot, counting what each stack has already given so a stack of 1 isn't used twice.
     * Each slot takes the first stack with an item to spare; when none has, a slot holding a stack it accepts moves to another, so a craft
     * is found whenever one exists, whatever order the items sit in. Returns null when some slot can't be filled.
     */
    public static @Nullable Plan plan(List<@Nullable Predicate<ItemStack>> slots, List<Inventory> inputs) {
        Matcher matcher = new Matcher(slots, stacks(inputs));
        for (int slot = 0; slot < 9; slot++) {
            if (matcher.wants(slot) != null && !matcher.assign(slot, new boolean[matcher.stacks.size()])) return null;
        }
        return matcher.plan();
    }

    private record Stack(Source source, ItemStack item) {}

    /** Every non-empty stack in the inputs, in order. Each input's contents are read once. */
    private static List<Stack> stacks(List<Inventory> inputs) {
        List<Stack> stacks = new ArrayList<>();
        for (Inventory inventory : inputs) {
            ItemStack[] contents = inventory.getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item != null && !item.isEmpty()) stacks.add(new Stack(new Source(inventory, slot), item));
            }
        }
        return stacks;
    }

    /** Matches recipe slots to stacks by augmenting paths: at most nine slots, so the search stays small. */
    private static final class Matcher {
        private final List<@Nullable Predicate<ItemStack>> slots;
        private final List<Stack> stacks;
        private final int[] used;
        /** Per recipe slot, the index of the stack it takes from, or -1. */
        private final int[] assigned = {-1, -1, -1, -1, -1, -1, -1, -1, -1};

        Matcher(List<@Nullable Predicate<ItemStack>> slots, List<Stack> stacks) {
            this.slots = slots;
            this.stacks = stacks;
            this.used = new int[stacks.size()];
        }

        @Nullable
        Predicate<ItemStack> wants(int slot) {
            return slot < slots.size() ? slots.get(slot) : null;
        }

        /** Fills {@code slot}, from a stack with an item to spare or by moving another slot off a stack it accepts. */
        boolean assign(int slot, boolean[] visited) {
            Predicate<ItemStack> wants = Objects.requireNonNull(wants(slot));
            for (int stack = 0; stack < stacks.size(); stack++) {
                if (!visited[stack] && used[stack] < stacks.get(stack).item().getAmount() && wants.test(stacks.get(stack).item())) {
                    used[stack]++;
                    assigned[slot] = stack;
                    return true;
                }
            }
            for (int stack = 0; stack < stacks.size(); stack++) {
                if (visited[stack] || !wants.test(stacks.get(stack).item())) continue;
                visited[stack] = true;
                for (int other = 0; other < 9; other++) {
                    // The other slot moves off this stack and this slot takes its item, so the stack's count is unchanged.
                    if (assigned[other] == stack && assign(other, visited)) {
                        assigned[slot] = stack;
                        return true;
                    }
                }
            }
            return false;
        }

        Plan plan() {
            List<@Nullable Source> sources = new ArrayList<>(9);
            @Nullable ItemStack[] matrix = new ItemStack[9];
            for (int slot = 0; slot < 9; slot++) {
                @Nullable Stack stack = assigned[slot] < 0 ? null : stacks.get(assigned[slot]);
                sources.add(stack == null ? null : stack.source());
                if (stack != null) matrix[slot] = stack.item().asOne();
            }
            return new Plan(sources, matrix);
        }
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
