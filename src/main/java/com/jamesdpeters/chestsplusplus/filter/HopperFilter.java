package com.jamesdpeters.chestsplusplus.filter;

import org.bukkit.inventory.ItemStack;

/** One hopper filter entry: a ghost item, whether it allows or denies, and how it matches (plan §5.5). */
public record HopperFilter(ItemStack template, Mode mode, Match match) {

    public enum Mode {
        ALLOW,
        DENY
    }

    /** How an entry matches items; clicking an entry in the editor cycles through these in order. */
    public enum Match {
        /** Same item including components ({@code isSimilar}). */
        EXACT,
        /** Same material. */
        TYPE,
        /** Shares an item-tag group (all logs, all wool, all seeds...). */
        SIMILAR;

        public Match next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public HopperFilter {
        template = template.asOne();
    }

    public HopperFilter withMatch(Match newMatch) {
        return new HopperFilter(template, mode, newMatch);
    }
}
