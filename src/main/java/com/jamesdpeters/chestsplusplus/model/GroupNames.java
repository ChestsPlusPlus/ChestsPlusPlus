package com.jamesdpeters.chestsplusplus.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Group name rules: up to 32 of {@code [A-Za-z0-9_-]} and single spaces between words, unique per owner and type, case-insensitive.
 * {@code :} stays out because it separates owner and name in {@code owner:name} references.
 */
public final class GroupNames {

    public static final int MAX_LENGTH = 32;
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]+( [A-Za-z0-9_-]+)*");

    private GroupNames() {}

    public static boolean isValid(String name) {
        return name.length() <= MAX_LENGTH && VALID.matcher(name).matches();
    }

    public static String normalise(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
