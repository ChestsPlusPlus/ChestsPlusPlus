package com.jamesdpeters.chestsplusplus.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Version comparison for the update checker: numeric dotted parts and SemVer pre-release precedence. */
public final class Versions {

    private Versions() {}

    /** Negative if {@code a} is older than {@code b}, zero if equal, positive if newer. */
    public static int compare(String a, String b) {
        Parsed pa = parse(a);
        Parsed pb = parse(b);
        int length = Math.max(pa.numbers.size(), pb.numbers.size());
        for (int i = 0; i < length; i++) {
            int x = i < pa.numbers.size() ? pa.numbers.get(i) : 0;
            int y = i < pb.numbers.size() ? pb.numbers.get(i) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        if (pa.preRelease.isEmpty() != pb.preRelease.isEmpty()) return pa.preRelease.isEmpty() ? 1 : -1;
        return comparePreRelease(pa.preRelease, pb.preRelease);
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    public static boolean isPreRelease(String version) {
        return !parse(version).preRelease.isEmpty();
    }

    private static int comparePreRelease(String a, String b) {
        String[] identifiersA = a.split("\\.");
        String[] identifiersB = b.split("\\.");
        for (int i = 0; i < Math.min(identifiersA.length, identifiersB.length); i++) {
            int comparison = compareIdentifier(identifiersA[i], identifiersB[i]);
            if (comparison != 0) return comparison;
        }
        return Integer.compare(identifiersA.length, identifiersB.length);
    }

    private static int compareIdentifier(String a, String b) {
        boolean numericA = a.matches("[0-9]+");
        boolean numericB = b.matches("[0-9]+");
        if (numericA != numericB) return numericA ? -1 : 1;
        if (numericA && a.length() != b.length()) return Integer.compare(a.length(), b.length());
        return a.compareTo(b);
    }

    private record Parsed(List<Integer> numbers, String preRelease) {}

    private static Parsed parse(String version) {
        String v = version.trim().toLowerCase(Locale.ROOT).split("\\+", 2)[0];
        if (v.startsWith("v")) v = v.substring(1);
        String pre = "";
        int dash = v.indexOf('-');
        if (dash >= 0) {
            pre = v.substring(dash + 1);
            v = v.substring(0, dash);
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : v.split("\\.")) {
            try {
                numbers.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                numbers.add(0);
            }
        }
        return new Parsed(numbers, pre);
    }
}
