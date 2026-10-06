package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Builds what each recipe slot accepts from its ghost item, the recipe's own choice there and the slot's {@link SlotMatch}. */
final class SlotRules {

    private SlotRules() {}

    /** One rule per slot, null for empty slots. Built once per recipe or mode change, so crafting allocates nothing. */
    static List<@Nullable Predicate<ItemStack>> of(@Nullable ItemStack[] matrix, SlotMatch[] matches, List<@Nullable Predicate<ItemStack>> choices) {
        List<@Nullable Predicate<ItemStack>> rules = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) rules.add(rule(matrix[i], i < choices.size() ? choices.get(i) : null, matches[i]));
        return rules;
    }

    static @Nullable Predicate<ItemStack> rule(@Nullable ItemStack ghost, @Nullable Predicate<ItemStack> choice, SlotMatch match) {
        if (ghost == null) return null;
        return switch (match.effective(choice != null)) {
            case RECIPE -> choice;
            case EXACT -> ghost.clone()::isSimilar;
            case TYPE -> sameType(ghost.getType());
        };
    }

    private static Predicate<ItemStack> sameType(Material type) {
        return item -> item.getType() == type;
    }
}
