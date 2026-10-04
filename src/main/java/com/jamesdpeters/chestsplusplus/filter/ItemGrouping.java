package com.jamesdpeters.chestsplusplus.filter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

/**
 * "Similar item" groups, precomputed once from Paper item tags. Two
 * materials are similar when they are equal or share a group. Lookups are an EnumMap get plus a BitSet intersect.
 */
public final class ItemGrouping {

    /** Item tags that make sensible "similar" groups (unknown keys are skipped, so this survives tag renames). */
    static final List<String> GROUPING_TAGS = List.of("logs", "planks", "wooden_slabs", "wooden_stairs", "wooden_fences", "wooden_doors",
            "wooden_trapdoors", "wooden_buttons", "wooden_pressure_plates", "saplings", "leaves", "wool", "wool_carpets", "beds", "banners",
            "candles", "terracotta", "small_flowers", "flowers", "coals", "fishes", "villager_plantable_seeds", "boats", "chest_boats", "arrows",
            "creeper_drop_music_discs", "anvil", "rails", "sand", "stone_bricks", "copper", "shulker_boxes", "skulls", "trim_templates",
            "decorated_pot_sherds", "harnesses", "bundles", "eggs", "dyes", "glass_panes", "concrete_powder", "smelts_to_glass", "iron_ores",
            "gold_ores", "copper_ores", "coal_ores", "diamond_ores", "emerald_ores", "lapis_ores", "redstone_ores");

    private static final BitSet EMPTY = new BitSet();
    private final Map<Material, BitSet> groups;

    /** Builds from explicit groups (tag key → materials); used directly by unit tests. */
    public ItemGrouping(Collection<? extends Set<Material>> materialGroups) {
        Map<Material, BitSet> map = new EnumMap<>(Material.class);
        int index = 0;
        for (Set<Material> group : materialGroups) {
            if (group.size() < 2) continue;
            for (Material material : group) map.computeIfAbsent(material, m -> new BitSet()).set(index);
            index++;
        }
        this.groups = map;
    }

    /** Builds from the server's item tags. */
    public static ItemGrouping fromServerTags() {
        List<Set<Material>> materialGroups = new ArrayList<>();
        for (String key : GROUPING_TAGS) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(key), Material.class);
            if (tag != null) materialGroups.add(Set.copyOf(tag.getValues()));
        }
        return new ItemGrouping(materialGroups);
    }

    public boolean similar(Material a, Material b) {
        return a == b || groups.getOrDefault(a, EMPTY).intersects(groups.getOrDefault(b, EMPTY));
    }

    public int groupCount(Material material) {
        return groups.getOrDefault(material, EMPTY).cardinality();
    }
}
