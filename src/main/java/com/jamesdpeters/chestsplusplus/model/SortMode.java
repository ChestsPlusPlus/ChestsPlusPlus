package com.jamesdpeters.chestsplusplus.model;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** How a ChestLink inventory is sorted on open/close and on "Sort now". */
public enum SortMode {
    OFF,
    NAME,
    AMOUNT_ASC,
    AMOUNT_DESC;

    /** The mode whose name is {@code text}, ignoring case, or null if there is none. */
    public static @Nullable SortMode parse(String text) {
        for (SortMode mode : values()) {
            if (mode.name().equalsIgnoreCase(text)) return mode;
        }
        return null;
    }

    /** The name players type and see, e.g. {@code amount_asc}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
